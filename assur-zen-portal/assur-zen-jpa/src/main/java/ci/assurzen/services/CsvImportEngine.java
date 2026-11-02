package ci.assurzen.services;

import ci.assurzen.entities.ImportErrorType;
import ci.assurzen.entities.SatisfactionImport;
import ci.assurzen.entities.SatisfactionImportError;
import ci.assurzen.entities.SatisfactionImportField;
import ci.assurzen.entities.SatisfactionImportMapping;
import ci.assurzen.entities.SatisfactionImportMappingValue;
import ci.assurzen.entities.SatisfactionImportStatut;
import ci.assurzen.repositories.SatisfactionImportErrorRepository;
import ci.assurzen.repositories.SatisfactionImportMappingRepository;
import ci.assurzen.repositories.SatisfactionImportMappingValueRepository;
import ci.assurzen.repositories.SatisfactionImportRepository;
import ci.assurzen.services.CsvImportRowProcessor.ColonneMappee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestre l'execution d'un import CSV : lecture/decodage du fichier, validation
 * encodage/separateur/colonnes obligatoires, resolution du mapping dynamique, puis boucle sur les
 * lignes. Generique dans sa forme (reutilisable pour de futurs modules Clients/Contrats/Sinistres/
 * Produits/Referentiels), mais cible concretement uniquement EnqueteSatisfaction pour l'instant.
 *
 * Le traitement de CHAQUE ligne est delegue a CsvImportRowProcessor, dans sa PROPRE transaction
 * (REQUIRES_NEW) — voir le commentaire de cette classe pour la raison (une ligne en echec DB ne
 * doit jamais faire echouer silencieusement tout le fichier).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportEngine {

    private final SatisfactionImportRepository satisfactionImportRepository;
    private final SatisfactionImportErrorRepository satisfactionImportErrorRepository;
    private final SatisfactionImportMappingRepository satisfactionImportMappingRepository;
    private final SatisfactionImportMappingValueRepository satisfactionImportMappingValueRepository;
    private final SatisfactionImportStorageService satisfactionImportStorageService;
    private final CsvImportRowProcessor csvImportRowProcessor;

    @Async("satisfactionImportTaskExecutor")
    public void executer(Long importId) {
        SatisfactionImport imp = satisfactionImportRepository.findById(importId).orElse(null);
        if (imp == null) {
            log.warn("Import satisfaction {} introuvable au moment du traitement asynchrone", importId);
            return;
        }

        imp = demarrer(imp);

        List<SatisfactionImportMapping> mappings = satisfactionImportMappingRepository
                .findByProfileIdOrderByOrdreAsc(imp.getMappingProfile().getId());

        List<String> records;
        try {
            records = decoderFichier(imp);
        } catch (Exception e) {
            enregistrerErreurFichier(imp, ImportErrorType.ENCODAGE_INVALIDE, "Encodage '" + imp.getEncoding() + "' invalide pour ce fichier: " + e.getMessage());
            finaliser(imp, 0, 0, 0, 1, SatisfactionImportStatut.FAILED, List.of());
            return;
        }

        if (records.isEmpty()) {
            enregistrerErreurFichier(imp, ImportErrorType.COLONNE_OBLIGATOIRE_ABSENTE, "Fichier vide");
            finaliser(imp, 0, 0, 0, 1, SatisfactionImportStatut.FAILED, List.of());
            return;
        }

        char delimiter = imp.getDelimiter().charAt(0);
        List<String> headerFields = CsvUtils.parseLine(records.get(0), delimiter).stream().map(String::trim).toList();
        if (headerFields.size() < 2) {
            enregistrerErreurFichier(imp, ImportErrorType.ENCODAGE_INVALIDE, "Separateur '" + delimiter + "' incoherent (une seule colonne detectee sur l'en-tete)");
            finaliser(imp, 0, 0, 0, 1, SatisfactionImportStatut.FAILED, List.of());
            return;
        }

        List<String> obligatoiresManquantes = mappings.stream()
                .filter(SatisfactionImportMapping::isObligatoire)
                .map(SatisfactionImportMapping::getCsvColumnName)
                .filter(col -> !headerFields.contains(col))
                .toList();
        if (!obligatoiresManquantes.isEmpty()) {
            enregistrerErreurFichier(imp, ImportErrorType.COLONNE_OBLIGATOIRE_ABSENTE, "Colonnes obligatoires absentes: " + obligatoiresManquantes);
            finaliser(imp, 0, 0, 0, 1, SatisfactionImportStatut.FAILED, List.of());
            return;
        }

        // Un champ (surtout COMMENT) peut etre alimente par PLUSIEURS colonnes CSV -> liste, pas une simple Map<Field,Integer>.
        Map<SatisfactionImportField, List<ColonneMappee>> mappingsParChamp = new EnumMap<>(SatisfactionImportField.class);
        Map<Long, Map<String, String>> tablesDeCorrespondance = new HashMap<>();
        for (SatisfactionImportMapping mapping : mappings) {
            int idx = headerFields.indexOf(mapping.getCsvColumnName());
            if (idx < 0) {
                continue;
            }
            mappingsParChamp.computeIfAbsent(mapping.getTargetField(), f -> new ArrayList<>())
                    .add(new ColonneMappee(mapping, idx));

            List<SatisfactionImportMappingValue> valeurs = satisfactionImportMappingValueRepository.findByMappingId(mapping.getId());
            if (!valeurs.isEmpty()) {
                Map<String, String> table = valeurs.stream().collect(Collectors.toMap(
                        v -> v.getValeurSource().trim().toLowerCase(), SatisfactionImportMappingValue::getValeurCible, (a, b) -> a));
                tablesDeCorrespondance.put(mapping.getId(), table);
            }
        }

        int dataStart = imp.isHasHeader() ? 1 : 0;
        List<String> dataRecords = records.subList(dataStart, records.size());

        int total = 0;
        int ignorees = 0;
        int reussies = 0;
        int enErreur = 0;
        List<Integer> scores = new ArrayList<>();

        for (int i = 0; i < dataRecords.size(); i++) {
            String rawRecord = dataRecords.get(i);
            int ligneNumero = i + 1;

            if (!StringUtils.hasText(rawRecord)) {
                ignorees++;
                continue;
            }
            List<String> fields = CsvUtils.parseLine(rawRecord, delimiter);
            if (fields.stream().noneMatch(StringUtils::hasText)) {
                ignorees++;
                continue;
            }

            total++;
            try {
                Integer score = csvImportRowProcessor.traiterLigne(imp, mappingsParChamp, tablesDeCorrespondance, fields);
                reussies++;
                if (score != null) {
                    scores.add(score);
                }
            } catch (ImportRowException e) {
                enErreur++;
                csvImportRowProcessor.enregistrerErreur(imp, ligneNumero, e);
            } catch (RuntimeException e) {
                enErreur++;
                csvImportRowProcessor.enregistrerErreur(imp, ligneNumero, new ImportRowException(ImportErrorType.ERREUR_INCONNUE, null, null, String.valueOf(e.getMessage())));
            }
        }

        SatisfactionImportStatut statutFinal;
        if (total == 0) {
            statutFinal = SatisfactionImportStatut.FAILED;
        } else if (enErreur == 0) {
            statutFinal = SatisfactionImportStatut.SUCCESS;
        } else if (reussies > 0) {
            statutFinal = SatisfactionImportStatut.PARTIAL_SUCCESS;
        } else {
            statutFinal = SatisfactionImportStatut.FAILED;
        }

        finaliser(imp, total, ignorees, reussies, enErreur, statutFinal, scores);
    }

    /**
     * Pas de @Transactional ici : appelee depuis executer() sur la MEME classe (auto-invocation),
     * qui contournerait le proxy Spring de toute facon (voir la meme lecon deja tiree pour @Async
     * dans CampagneMarketingService). Sans consequence : chaque appel repository est deja
     * transactionnel individuellement via Spring Data.
     *
     * IMPORTANT : SatisfactionImport a un @Version (AuditableEntity) — save() retourne une NOUVELLE
     * instance avec la version incrementee ; l'appelant DOIT reaffecter son "imp" avec le retour,
     * sinon le save() suivant (dans finaliser()) echoue avec ObjectOptimisticLockingFailureException
     * (constate en test le 2026-08-06 : import reste bloque a RUNNING indefiniment).
     */
    private SatisfactionImport demarrer(SatisfactionImport imp) {
        imp.setStatut(SatisfactionImportStatut.RUNNING);
        imp.setDateDebutTraitement(Instant.now());
        imp = satisfactionImportRepository.save(imp);
        satisfactionImportErrorRepository.deleteBySatisfactionImportId(imp.getId());
        return imp;
    }

    private List<String> decoderFichier(SatisfactionImport imp) throws Exception {
        byte[] bytes = satisfactionImportStorageService.read(imp.getStoredFilePath());
        Charset charset = Charset.forName(imp.getEncoding());
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String content = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        return decouperEnEnregistrements(content);
    }

    /**
     * Decoupe le contenu en "enregistrements" CSV logiques en respectant les guillemets : un saut
     * de ligne A L'INTERIEUR d'un champ entre guillemets ne doit PAS demarrer un nouvel
     * enregistrement (contrairement a un simple content.lines()). Constate sur un vrai export
     * Google Forms ou un commentaire contient un retour a la ligne.
     */
    private List<String> decouperEnEnregistrements(String content) {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '\n' && !inQuotes) {
                records.add(current.toString());
                current.setLength(0);
            } else if (c == '\r') {
                // ignore : normalise CRLF, le \n associe declenchera la coupure
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            records.add(current.toString());
        }
        return records;
    }

    private void enregistrerErreurFichier(SatisfactionImport imp, ImportErrorType type, String message) {
        satisfactionImportErrorRepository.save(SatisfactionImportError.builder()
                .satisfactionImport(imp)
                .ligneNumero(0)
                .typeErreur(type)
                .message(message)
                .build());
    }

    /** "Recalculer le NPS" : promoteurs (9-10) / passifs (7-8) / detracteurs (0-6) sur les scores traites lors de CE run. */
    private void finaliser(SatisfactionImport imp, int total, int ignorees, int reussies, int enErreur,
                            SatisfactionImportStatut statut, List<Integer> scores) {
        imp.setTotalLignes(total);
        imp.setLignesIgnorees(ignorees);
        imp.setLignesReussies(reussies);
        imp.setLignesEnErreur(enErreur);
        imp.setStatut(statut);
        imp.setDateFinTraitement(Instant.now());
        imp.setPromoteurs((int) scores.stream().filter(s -> s >= 9).count());
        imp.setPassifs((int) scores.stream().filter(s -> s >= 7 && s <= 8).count());
        imp.setDetracteurs((int) scores.stream().filter(s -> s <= 6).count());
        imp.setScoreNpsMoyen(scores.isEmpty() ? null : scores.stream().mapToInt(Integer::intValue).average().orElse(0));
        satisfactionImportRepository.save(imp);
    }
}
