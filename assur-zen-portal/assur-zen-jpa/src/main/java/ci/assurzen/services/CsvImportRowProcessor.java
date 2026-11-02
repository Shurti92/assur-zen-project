package ci.assurzen.services;

import ci.assurzen.entities.Contract;
import ci.assurzen.entities.DuplicateStrategy;
import ci.assurzen.entities.EnqueteContexte;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.entities.ImportErrorType;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.SatisfactionImport;
import ci.assurzen.entities.SatisfactionImportError;
import ci.assurzen.entities.SatisfactionImportField;
import ci.assurzen.entities.SatisfactionImportMapping;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.EnqueteSatisfactionRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.SatisfactionImportErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Traitement d'UNE ligne CSV, isole dans sa PROPRE transaction (REQUIRES_NEW). Separe de
 * CsvImportEngine expres : si cette logique restait dans la meme transaction englobante que
 * l'ensemble du fichier, une seule ligne en echec DB reel (ex: contrainte violee) marque la
 * session Hibernate "rollback-only" et annule silencieusement TOUT l'import (les 62 autres lignes
 * y compris), constate en test le 2026-08-06 (UnexpectedRollbackException a la fin de executer(),
 * import reste bloque a PENDING). Avec REQUIRES_NEW, seule la ligne fautive est perdue.
 */
@Service
@RequiredArgsConstructor
class CsvImportRowProcessor {

    private static final ZoneId ZONE = ZoneId.of("Africa/Abidjan");

    private static final DateTimeFormatter[] DATETIME_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    };
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    };
    private static final DateTimeFormatter GOOGLE_FORMS_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd h:mm:ss a", Locale.ENGLISH);
    private static final Pattern TELEPHONE_PATTERN = Pattern.compile("^(0\\d{9}|\\+225\\d{10}|00225\\d{10})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final EnqueteSatisfactionRepository enqueteSatisfactionRepository;
    private final SatisfactionImportErrorRepository satisfactionImportErrorRepository;
    private final CodeGeneratorService codeGeneratorService;

    /** Une colonne CSV mappee, avec son index resolu dans l'en-tete de CE fichier. */
    record ColonneMappee(SatisfactionImportMapping mapping, int index) {
    }

    /** @return le score de la ligne si un enregistrement a ete cree/mis a jour, null si aucun score ou ligne ignoree (doublon). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer traiterLigne(SatisfactionImport imp, Map<SatisfactionImportField, List<ColonneMappee>> mappingsParChamp,
                                 Map<Long, Map<String, String>> tablesDeCorrespondance, List<String> fields) {
        String responseDateRaw = valeurUnique(fields, mappingsParChamp, SatisfactionImportField.RESPONSE_DATE);
        String customerName = valeurUnique(fields, mappingsParChamp, SatisfactionImportField.CUSTOMER_NAME);
        String phone = valeurUnique(fields, mappingsParChamp, SatisfactionImportField.PHONE);
        String email = valeurUnique(fields, mappingsParChamp, SatisfactionImportField.EMAIL);
        String policyNumber = valeurUnique(fields, mappingsParChamp, SatisfactionImportField.POLICY_NUMBER);
        String comment = valeurConcatenee(fields, mappingsParChamp, SatisfactionImportField.COMMENT);

        Instant responseDate = parseResponseDate(responseDateRaw);
        Integer score = resoudreScore(fields, mappingsParChamp, tablesDeCorrespondance);

        if (StringUtils.hasText(phone) && !isTelephoneValide(phone)) {
            throw new ImportRowException(ImportErrorType.TELEPHONE_INVALIDE, "phone", phone, "Numero de telephone invalide: " + phone);
        }
        if (StringUtils.hasText(email) && !isEmailValide(email)) {
            throw new ImportRowException(ImportErrorType.EMAIL_INVALIDE, "email", email, "Adresse email invalide: " + email);
        }

        Insured insured = rechercherClient(phone, customerName);
        if (insured == null) {
            throw new ImportRowException(ImportErrorType.CLIENT_INTROUVABLE, "customerName/phone",
                    (customerName == null ? "" : customerName) + " / " + (phone == null ? "" : phone), "Aucun assure trouve pour ce telephone/nom");
        }

        Contract contract = null;
        if (StringUtils.hasText(policyNumber)) {
            contract = contractRepository.findByPolicyNumber(policyNumber).orElse(null);
            if (contract == null) {
                throw new ImportRowException(ImportErrorType.CONTRAT_INTROUVABLE, "policyNumber", policyNumber, "Aucun contrat trouve pour ce numero de police");
            }
        }

        Instant dateEffective = responseDate == null ? Instant.now() : responseDate;
        EnqueteSatisfaction doublon = trouverDoublon(insured, contract);
        if (doublon != null) {
            switch (imp.getDuplicateStrategy()) {
                case IGNORER -> {
                    return null;
                }
                case ERREUR -> throw new ImportRowException(ImportErrorType.DOUBLON, null, null,
                        "Une reponse de satisfaction existe deja pour cet assure" + (contract != null ? "/contrat" : ""));
                case ECRASER -> {
                    doublon.setScoreNps(score);
                    doublon.setCommentaire(comment);
                    doublon.setStatut(EnqueteStatut.REPONDUE);
                    doublon.setDateReponse(dateEffective);
                    enqueteSatisfactionRepository.save(doublon);
                    return score;
                }
            }
        }

        EnqueteSatisfaction enquete = EnqueteSatisfaction.builder()
                .code(codeGeneratorService.generate(CodePrefix.ENQUETE))
                .contexte(EnqueteContexte.PERIODIQUE)
                .insured(insured)
                .contrat(contract)
                .dateEnvoi(dateEffective)
                .dateReponse(dateEffective)
                .statut(EnqueteStatut.REPONDUE)
                .scoreNps(score)
                .commentaire(comment)
                .build();
        enqueteSatisfactionRepository.save(enquete);
        return score;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerErreur(SatisfactionImport imp, int ligneNumero, ImportRowException e) {
        satisfactionImportErrorRepository.save(SatisfactionImportError.builder()
                .satisfactionImport(imp)
                .ligneNumero(ligneNumero)
                .colonneEnErreur(e.getColonne())
                .valeurBrute(e.getValeurBrute())
                .typeErreur(e.getType())
                .message(e.getMessage())
                .build());
    }

    private String valeurUnique(List<String> fields, Map<SatisfactionImportField, List<ColonneMappee>> mappingsParChamp, SatisfactionImportField field) {
        List<ColonneMappee> colonnes = mappingsParChamp.get(field);
        if (colonnes == null || colonnes.isEmpty()) {
            return null;
        }
        return CsvUtils.value(fields, colonnes.get(0).index());
    }

    /** Plusieurs colonnes peuvent cibler COMMENT (ex: plusieurs questions/reponses d'un formulaire) : concatenees avec le nom de colonne d'origine en prefixe. */
    private String valeurConcatenee(List<String> fields, Map<SatisfactionImportField, List<ColonneMappee>> mappingsParChamp, SatisfactionImportField field) {
        List<ColonneMappee> colonnes = mappingsParChamp.get(field);
        if (colonnes == null || colonnes.isEmpty()) {
            return null;
        }
        String joined = colonnes.stream()
                .map(c -> {
                    String v = CsvUtils.value(fields, c.index());
                    return v == null ? null : c.mapping().getCsvColumnName() + ": " + v;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | "));
        return joined.isEmpty() ? null : joined;
    }

    /**
     * Resout le score 0-10 pour la colonne mappee sur SCORE. Si une table de correspondance existe
     * pour ce mapping (SatisfactionImportMappingValue), la valeur brute doit y figurer (sinon
     * NOTE_INVALIDE) — necessaire car la plupart des formulaires reels n'exportent pas une note
     * numerique brute mais des reponses textuelles/etoiles a choix multiple. Sans table configuree,
     * comportement d'origine : parse direct de la valeur brute en entier 0-10.
     */
    private Integer resoudreScore(List<String> fields, Map<SatisfactionImportField, List<ColonneMappee>> mappingsParChamp,
                                   Map<Long, Map<String, String>> tablesDeCorrespondance) {
        List<ColonneMappee> colonnes = mappingsParChamp.get(SatisfactionImportField.SCORE);
        if (colonnes == null || colonnes.isEmpty()) {
            return null;
        }
        ColonneMappee colonne = colonnes.get(0);
        String raw = CsvUtils.value(fields, colonne.index());
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        Map<String, String> table = tablesDeCorrespondance.get(colonne.mapping().getId());
        if (table != null) {
            String mapped = table.get(normaliserValeur(raw));
            if (mapped == null) {
                throw new ImportRowException(ImportErrorType.NOTE_INVALIDE, "score", raw,
                        "Valeur '" + raw + "' absente de la table de correspondance configuree pour ce mapping");
            }
            return parseScore(mapped);
        }
        return parseScore(raw);
    }

    private String normaliserValeur(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Instant parseResponseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();

        int utcIdx = trimmed.indexOf(" UTC");
        if (utcIdx > 0) {
            String datePart = trimmed.substring(0, utcIdx).trim();
            try {
                return LocalDateTime.parse(datePart, GOOGLE_FORMS_FORMAT).atZone(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
                // essai des formats generiques ci-dessous
            }
        }

        for (DateTimeFormatter fmt : DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, fmt).atZone(ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // essai du format suivant
            }
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt).atStartOfDay(ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // essai du format suivant
            }
        }
        throw new ImportRowException(ImportErrorType.FORMAT_DATE_INVALIDE, "responseDate", raw, "Format de date non reconnu: " + raw);
    }

    private Integer parseScore(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int score;
        try {
            score = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ImportRowException(ImportErrorType.NOTE_INVALIDE, "score", raw, "Note non numerique: " + raw);
        }
        if (score < 0 || score > 10) {
            throw new ImportRowException(ImportErrorType.NOTE_INVALIDE, "score", raw, "Note hors plage 0-10: " + raw);
        }
        return score;
    }

    private boolean isTelephoneValide(String raw) {
        return TELEPHONE_PATTERN.matcher(raw.trim().replaceAll("\\s+", "")).matches();
    }

    private boolean isEmailValide(String raw) {
        return EMAIL_PATTERN.matcher(raw.trim()).matches();
    }

    private Insured rechercherClient(String phone, String customerName) {
        if (StringUtils.hasText(phone)) {
            // findByClientPhone() suppose l'unicite (Optional<Insured>) - plusieurs assures peuvent
            // legitimement partager un meme numero en base (pas de contrainte UNIQUE dessus), ce qui
            // fait remonter IncorrectResultSizeDataAccessException jusqu'au catch-all generique de
            // CsvImportEngine (ERREUR_INCONNUE, message technique Hibernate brut - bug rapporte par le
            // frontend). On le convertit ici en erreur metier dediee plutot que de fusionner
            // silencieusement avec le premier trouve (ambiguite reelle, pas a deviner).
            Insured direct;
            try {
                direct = insuredRepository.findByClientPhone(phone.trim()).orElse(null);
            } catch (IncorrectResultSizeDataAccessException e) {
                throw new ImportRowException(ImportErrorType.CLIENT_AMBIGU, "telephone", phone,
                        "Plusieurs assures partagent ce numero de telephone, impossible de determiner le bon client: " + phone);
            }
            if (direct != null) {
                return direct;
            }
            String normalized = normaliserTelephone(phone);
            Insured parNormalisation = insuredRepository.findAll().stream()
                    .filter(i -> normalized.equals(normaliserTelephone(i.getClientPhone())))
                    .findFirst().orElse(null);
            if (parNormalisation != null) {
                return parNormalisation;
            }
        }
        if (StringUtils.hasText(customerName)) {
            String normalizedName = normaliserNom(customerName);
            return insuredRepository.findAll().stream()
                    .filter(i -> normaliserNom(i.getNom() + " " + i.getPrenoms()).contains(normalizedName)
                            || normaliserNom(i.getPrenoms() + " " + i.getNom()).contains(normalizedName))
                    .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Le format ivoirien issu de la renumerotation de 2021 conserve le 0 initial du numero
     * d'abonne meme derriere l'indicatif +225 (ex: +2250708218762 = +225 puis 0708218762, PAS
     * +225 puis 708218762) — meme regle que SmsSenderService.normaliserAuFormatInternational.
     * Ne pas re-ajouter de "0" apres avoir retire le prefixe, il y est deja.
     */
    private String normaliserTelephone(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00225")) {
            digits = digits.substring(5);
        } else if (digits.startsWith("225") && digits.length() >= 13) {
            digits = digits.substring(3);
        }
        return digits;
    }

    private String normaliserNom(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private EnqueteSatisfaction trouverDoublon(Insured insured, Contract contract) {
        return enqueteSatisfactionRepository.findByInsuredId(insured.getId()).stream()
                .filter(e -> e.getStatut() == EnqueteStatut.REPONDUE)
                .filter(e -> Objects.equals(contract == null ? null : contract.getId(), e.getContrat() == null ? null : e.getContrat().getId()))
                .findFirst()
                .orElse(null);
    }
}
