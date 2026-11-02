package ci.assurzen.services;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ImportRowError;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartenaireDocumentContent;
import ci.assurzen.dto.PartenaireDocumentResponse;
import ci.assurzen.dto.PartenaireRequest;
import ci.assurzen.dto.PartenaireResponse;
import ci.assurzen.dto.UserStatusRequest;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireDocument;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.TypeProfile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.repositories.PartenaireDocumentRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.TypeProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartenaireService {

    /** Mot de passe par defaut de tous les comptes partenaires crees automatiquement - tranche avec le produit, cf. contrat "Creation de comptes utilisateurs" §1. */
    private static final String MOT_DE_PASSE_PARTENAIRE_DEFAUT = "assur@zen0";
    private static final String TEMPLATE_BIENVENUE_PARTENAIRE = "BIENVENUE_PARTENAIRE_MOBILE";

    /** Lien inclus dans le SMS de bienvenue partenaire (contrat "Lien application dans le SMS de
     * bienvenue partenaire", 2026-08-30) - diffère entre prod et recette/sandbox, cf. application*.yml. */
    @Value("${app.partenaire.lien-connexion}")
    private String lienConnexionPartenaire;

    private final PartenaireRepository repository;
    private final ProfileRepository profileRepository;
    private final ContractRepository contractRepository;
    private final ExerciceComptableRepository exerciceComptableRepository;
    private final VillePartenaireRepository villePartenaireRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final TypeProfileRepository typeProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsNotificationService smsNotificationService;
    private final UserProfileService userProfileService;
    private final AuthService authService;
    private final PasswordHistoryService passwordHistoryService;
    private final PartenaireDocumentRepository partenaireDocumentRepository;
    private final PartenaireDocumentStorageService partenaireDocumentStorageService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public PageResponse<PartenaireResponse> search(String q, Boolean active, PartenaireType type, Integer page, Integer size) {
        return search(q, active, type, null, page, size);
    }

    /** documentsEnAttente : true = seulement les partenaires ayant au moins un document a verifier, false = aucun,
     * null = pas de filtre (2026-10-05). */
    public PageResponse<PartenaireResponse> search(String q, Boolean active, PartenaireType type, Boolean documentsEnAttente,
                                                   Integer page, Integer size) {
        List<PartenaireResponse> lignes = filter(q, active, type);
        if (documentsEnAttente != null) {
            lignes = lignes.stream().filter(p -> (p.documentsEnAttente() > 0) == documentsEnAttente).toList();
        }
        return paginate(lignes, page, size);
    }

    /**
     * Liste non paginee (contrairement a search(), plafonnee a 200 lignes) : reservee aux agregations
     * internes backend, ex. DashboardBackOfficeService, qui ont besoin de la totalite des partenaires.
     */
    public List<PartenaireResponse> findAllActifs() {
        return filter(null, true, null);
    }

    public byte[] exportCsv(String q, Boolean active, PartenaireType type) {
        List<PartenaireResponse> rows = filter(q, active, type);

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Id;CodePartenaire;Type;Nom;Prenoms;ObjectifProduction;ProfileId;Actif\n");
        for (PartenaireResponse row : rows) {
            csv.append(CsvUtils.field(row.id()))
                    .append(';').append(CsvUtils.field(row.codePartenaire()))
                    .append(';').append(CsvUtils.field(row.type()))
                    .append(';').append(CsvUtils.field(row.nom()))
                    .append(';').append(CsvUtils.field(row.prenoms()))
                    .append(';').append(CsvUtils.field(row.objectifProduction()))
                    .append(';').append(CsvUtils.field(row.profileId()))
                    .append(';').append(CsvUtils.field(row.active()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportResult importCsv(MultipartFile file) {
        requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV manquant ou vide");
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier CSV");
        }

        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        for (int i = 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (!StringUtils.hasText(rawLine)) {
                continue;
            }
            totalRows++;
            int lineNumber = i + 1;

            List<String> fields = CsvUtils.parseLine(rawLine);
            String codePartenaire = CsvUtils.value(fields, 0);
            String typeRaw = CsvUtils.value(fields, 1);
            String nom = CsvUtils.value(fields, 2);
            String prenoms = CsvUtils.value(fields, 3);
            String objectifRaw = CsvUtils.value(fields, 4);
            String profileIdRaw = CsvUtils.value(fields, 5);
            Boolean active = parseBoolean(CsvUtils.value(fields, 6));

            if (!StringUtils.hasText(typeRaw) || !StringUtils.hasText(nom) || !StringUtils.hasText(prenoms)) {
                errors.add(new ImportRowError(lineNumber, nom, "Champs obligatoires manquants (type, nom, prenoms)"));
                continue;
            }

            PartenaireType type;
            try {
                type = PartenaireType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportRowError(lineNumber, nom, "Type invalide (attendu: DIRECT, AGENT ou APPORTEUR): " + typeRaw));
                continue;
            }

            if (type != PartenaireType.APPORTEUR && !StringUtils.hasText(objectifRaw)) {
                errors.add(new ImportRowError(lineNumber, nom, "objectifProduction obligatoire pour ce type de partenaire"));
                continue;
            }

            BigDecimal objectifProduction = null;
            if (StringUtils.hasText(objectifRaw)) {
                try {
                    objectifProduction = new BigDecimal(objectifRaw.replace(",", "."));
                } catch (NumberFormatException e) {
                    errors.add(new ImportRowError(lineNumber, nom, "objectifProduction invalide: " + objectifRaw));
                    continue;
                }
            }

            Long profileId = null;
            if (StringUtils.hasText(profileIdRaw)) {
                try {
                    profileId = Long.valueOf(profileIdRaw);
                } catch (NumberFormatException e) {
                    errors.add(new ImportRowError(lineNumber, nom, "profileId invalide: " + profileIdRaw));
                    continue;
                }
            }

            try {
                create(new PartenaireRequest(codePartenaire, type, nom, prenoms, objectifProduction, profileId, null, active, null, null, null, null, null, null, null));
                successCount++;
            } catch (ResponseStatusException e) {
                errors.add(new ImportRowError(lineNumber, nom, e.getReason()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(lineNumber, nom, "Erreur inattendue: " + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, successCount, errors.size(), errors);
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "oui".equalsIgnoreCase(value);
    }

    private List<PartenaireResponse> filter(String q, Boolean active, PartenaireType type) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;

        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .filter(partenaire -> active == null || partenaire.isActive() == active)
                .filter(partenaire -> type == null || partenaire.getType() == type)
                .filter(partenaire -> normalizedQuery == null
                        || contains(partenaire.getNom(), normalizedQuery)
                        || contains(partenaire.getPrenoms(), normalizedQuery)
                        || contains(partenaire.getCodePartenaire(), normalizedQuery))
                .map(this::toResponse)
                .toList();
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    public PartenaireResponse findById(Long id) {
        verifierAccesPartenaireCible(id);
        return toResponse(getPartenaire(id));
    }

    public List<PartenaireDocumentResponse> listDocuments(Long id) {
        verifierAccesPartenaireCible(id);
        getPartenaire(id);
        return partenaireDocumentRepository.findByPartenaireIdOrderByCreatedAtAsc(id).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional
    public PartenaireDocumentResponse ajouterDocument(Long id, String type, MultipartFile file) {
        return ajouterDocument(id, type, file, null);
    }

    @Transactional
    public PartenaireDocumentResponse ajouterDocument(Long id, String type, MultipartFile file, java.time.LocalDate dateExpiration) {
        verifierAccesPartenaireCible(id);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        if (!StringUtils.hasText(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type de document est requis");
        }
        long maxFileSize = dynamicConfigurationService.getLong("MAX_FILE_SIZE", 10L * 1024 * 1024);
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier trop volumineux (" + file.getSize() + " octets, maximum autorise: " + maxFileSize + " octets, configurable via MAX_FILE_SIZE)");
        }
        // Seuls PDF, JPEG et PNG authentiques (extension + type declare + signature) - 2026-10-05.
        String typeControle;
        try (java.io.InputStream debut = file.getInputStream()) {
            typeControle = ci.assurzen.utils.DocumentFichierValidator.verifier(file.getOriginalFilename(), file.getContentType(),
                    debut.readNBytes(8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Partenaire partenaire = getPartenaire(id);
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();

        String storedPath;
        try {
            storedPath = partenaireDocumentStorageService.store(id, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        PartenaireDocument document = PartenaireDocument.builder()
                .partenaire(partenaire)
                .type(type)
                .fileName(ci.assurzen.utils.DocumentFichierValidator.nettoyerNom(file.getOriginalFilename()))
                .storedPath(storedPath)
                .contentType(typeControle)
                .sizeBytes(file.getSize())
                .uploadedBy(resolveUser(principal.userId()))
                .dateExpiration(dateExpiration)
                .build();
        // Statut de verification (2026-10-04) : un document joint par un administrateur est valide d'office ;
        // fourni par le partenaire, il attend la verification d'AssurZen.
        if (isAdmin(principal)) {
            document.setStatutValidation(ci.assurzen.entities.StatutValidationDocument.VALIDE);
            document.setValideLe(Instant.now());
            document.setValidePar(document.getUploadedBy());
        } else {
            document.setStatutValidation(ci.assurzen.entities.StatutValidationDocument.EN_ATTENTE);
        }

        return toDocumentResponse(partenaireDocumentRepository.save(document));
    }

    public PartenaireDocumentContent getDocumentContent(Long id, Long documentId) {
        verifierAccesPartenaireCible(id);
        PartenaireDocument document = partenaireDocumentRepository.findByIdAndPartenaireId(documentId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable: " + documentId));
        return new PartenaireDocumentContent(partenaireDocumentStorageService.read(document.getStoredPath()), document.getContentType(), document.getFileName());
    }

    /** Fixe (ou efface, si null) la date d'expiration d'un document deja joint - 2026-10-03. */
    @Transactional
    public PartenaireDocumentResponse definirDateExpiration(Long id, Long documentId, java.time.LocalDate dateExpiration) {
        verifierAccesPartenaireCible(id);
        PartenaireDocument document = partenaireDocumentRepository.findByIdAndPartenaireId(documentId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable: " + documentId));
        document.setDateExpiration(dateExpiration);
        return toDocumentResponse(partenaireDocumentRepository.save(document));
    }

    /**
     * Verification d'un document par un administrateur (2026-10-04) : VALIDE, ou REFUSE avec un motif
     * obligatoire (notification DOCUMENT_REFUSE au partenaire, creee apres commit). Revalider un document
     * refuse (ou l'inverse) est permis.
     */
    @Transactional
    public PartenaireDocumentResponse validerDocument(Long id, Long documentId, ci.assurzen.entities.StatutValidationDocument statut, String motif) {
        requireAdmin();
        if (statut != ci.assurzen.entities.StatutValidationDocument.VALIDE && statut != ci.assurzen.entities.StatutValidationDocument.REFUSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statut attendu : VALIDE ou REFUSE");
        }
        boolean refus = statut == ci.assurzen.entities.StatutValidationDocument.REFUSE;
        if (refus && !StringUtils.hasText(motif)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif du refus est obligatoire");
        }
        if (refus && motif.trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motif trop long (500 caracteres maximum)");
        }
        PartenaireDocument document = partenaireDocumentRepository.findByIdAndPartenaireId(documentId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable: " + documentId));
        document.setStatutValidation(statut);
        document.setMotifRefus(refus ? motif.trim() : null);
        document.setValideLe(Instant.now());
        document.setValidePar(resolveUser(authService.currentPrincipal().userId()));
        PartenaireDocument saved = partenaireDocumentRepository.save(document);
        if (refus) {
            eventPublisher.publishEvent(ci.assurzen.events.NotificationUtilisateurEvent.pourPartenaire(id,
                    ci.assurzen.entities.PartenaireNotificationType.DOCUMENT_REFUSE, "Document refusé",
                    "Votre document " + saved.getType() + " a été refusé : " + saved.getMotifRefus()
                            + ". Merci d'en fournir un nouveau.",
                    null, "DOCUMENT", saved.getId()));
        }
        return toDocumentResponse(saved);
    }

    /** Suppression physique simple (pas de restriction metier particuliere, contrairement a
     * DELETE /reclamations/{id} : un document partenaire n'a pas d'entite dependante equivalente a un
     * commentaire/piece jointe qui justifierait un 409) - meme garde-fou d'acces que les 3 autres
     * endpoints documents (verifierAccesPartenaireCible). Le fichier sur disque est supprime en
     * best-effort apres la ligne en base ; un souci de suppression disque ne doit jamais faire
     * echouer l'operation vue par l'utilisateur. */
    @Transactional
    public void deleteDocument(Long id, Long documentId) {
        verifierAccesPartenaireCible(id);
        PartenaireDocument document = partenaireDocumentRepository.findByIdAndPartenaireId(documentId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable: " + documentId));

        partenaireDocumentRepository.delete(document);
        partenaireDocumentStorageService.delete(document.getStoredPath());
    }

    @Transactional
    public PartenaireResponse create(PartenaireRequest request) {
        requireAdmin();
        validateObjectifProduction(request.type(), request.objectifProduction());

        String codePartenaire = StringUtils.hasText(request.codePartenaire())
                ? request.codePartenaire()
                : codeGeneratorService.generate(CodePrefix.PARTNER);

        repository.findByCodePartenaire(codePartenaire)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un partenaire existe deja pour ce code: " + codePartenaire);
                });

        Partenaire partenaire = Partenaire.builder()
                .codePartenaire(codePartenaire)
                .type(request.type())
                .nom(request.nom())
                .prenoms(request.prenoms())
                .objectifProduction(request.objectifProduction())
                .profile(resolveProfileUnique(request.profileId(), null))
                .typeProfile(resolveTypeProfile(request.typeProfileId()))
                .telephone(request.telephone())
                .numeroWaveCommission(request.numeroWaveCommission())
                .exerciceComptable(resolveExerciceComptable(request.exerciceComptableId()))
                .villePartenaire(resolveVillePartenaire(request.villePartenaireId()))
                .active(request.active() == null || request.active())
                .frequencePaiementCommission(request.frequencePaiementCommission() != null
                        ? request.frequencePaiementCommission() : FrequencePaiementCommission.MENSUELLE)
                .accesActiviteCommerciale(Boolean.TRUE.equals(request.accesActiviteCommerciale()))
                .primeObjectifActivee(Boolean.TRUE.equals(request.primeObjectifActivee()))
                .build();
        partenaire = repository.save(partenaire);

        ResultatCreationCompte resultat = tenterCreationCompteAutomatique(partenaire);
        if (resultat != null && resultat.succes()) {
            partenaire.setProfile(resultat.profile());
            partenaire = repository.save(partenaire);
        }

        return toResponse(partenaire, resultat);
    }

    @Transactional
    public PartenaireResponse update(Long id, PartenaireRequest request) {
        requireAdmin();
        validateObjectifProduction(request.type(), request.objectifProduction());
        Partenaire partenaire = getPartenaire(id);

        String codePartenaire = StringUtils.hasText(request.codePartenaire()) ? request.codePartenaire() : partenaire.getCodePartenaire();
        repository.findByCodePartenaire(codePartenaire)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un partenaire existe deja pour ce code: " + codePartenaire);
                });

        partenaire.setCodePartenaire(codePartenaire);
        partenaire.setType(request.type());
        partenaire.setNom(request.nom());
        partenaire.setPrenoms(request.prenoms());
        partenaire.setObjectifProduction(request.objectifProduction());
        // profileId omis (null) dans la requete : on NE dissocie PAS le profil deja lie - sinon un
        // PUT qui ne connait pas ce champ (ex. formulaire modifiant seulement telephone/typeProfileId)
        // deconnecterait silencieusement un compte deja cree, puis tenterCreationCompteAutomatique()
        // le recreerait/echouerait en conflit sur le meme telephone (bug reel observe en E2E). Un
        // profileId explicite reste toujours pris en compte normalement.
        if (request.profileId() != null) {
            partenaire.setProfile(resolveProfileUnique(request.profileId(), id));
        }
        partenaire.setTypeProfile(resolveTypeProfile(request.typeProfileId()));
        partenaire.setTelephone(request.telephone());
        partenaire.setNumeroWaveCommission(request.numeroWaveCommission());
        partenaire.setExerciceComptable(resolveExerciceComptable(request.exerciceComptableId()));
        partenaire.setVillePartenaire(resolveVillePartenaire(request.villePartenaireId()));
        partenaire.setActive(request.active() == null || request.active());
        // Absent (null) sur ces deux champs => valeur existante preservee, meme logique que profileId
        // ci-dessus : un PUT qui ignore ces champs (vieux client, script) ne doit pas silencieusement
        // reinitialiser la frequence ni, pire, declencher la desactivation en cascade du compte.
        if (request.frequencePaiementCommission() != null) {
            partenaire.setFrequencePaiementCommission(request.frequencePaiementCommission());
        }
        if (request.accesActiviteCommerciale() != null) {
            partenaire.setAccesActiviteCommerciale(request.accesActiviteCommerciale());
        }
        if (request.primeObjectifActivee() != null) {
            partenaire.setPrimeObjectifActivee(request.primeObjectifActivee());
        }
        partenaire = repository.save(partenaire);

        ResultatCreationCompte resultat = tenterCreationCompteAutomatique(partenaire);
        if (resultat != null && resultat.succes()) {
            partenaire.setProfile(resultat.profile());
            partenaire = repository.save(partenaire);
        }

        return toResponse(partenaire, resultat);
    }

    /**
     * Side-effect de create()/update() (contrat "Creation de comptes utilisateurs" §3.2, etendu par
     * "Frequence de paiement de commission" §2) : cree un compte de connexion (username=telephone,
     * mot de passe par defaut, role USER) quand le partenaire est eligible (cf. estEligibleCreationCompte)
     * ET qu'aucun compte n'est deja lie. Si un compte est deja lie, synchronise plutot son etat
     * actif/inactif sur l'eligibilite courante (creation/desactivation en cascade, cf. §2.4).
     * Retourne null si aucune TENTATIVE DE CREATION n'etait applicable (pas de declenchement, compte
     * deja lie - idempotent sur la creation). Ne leve JAMAIS d'exception : un conflit (telephone deja
     * utilise comme username) ou toute erreur inattendue est capture et retourne comme un echec
     * signale, pour ne jamais faire echouer la sauvegarde du partenaire lui-meme (decision produit
     * tranchee, §6.2).
     */
    private ResultatCreationCompte tenterCreationCompteAutomatique(Partenaire partenaire) {
        boolean eligible = estEligibleCreationCompte(partenaire);

        if (partenaire.getProfile() != null) {
            synchroniserActivationCompte(partenaire, eligible);
            return null;
        }
        if (!eligible) {
            return null;
        }
        if (!StringUtils.hasText(partenaire.getTelephone())) {
            return new ResultatCreationCompte(false, null, "Impossible de creer le compte : aucun numero de telephone renseigne sur le partenaire");
        }

        try {
            if (userRepository.findByUsername(partenaire.getTelephone()).isPresent()) {
                return new ResultatCreationCompte(false, null,
                        "Ce numero de telephone est deja utilise par un autre compte - corrigez-le depuis la fiche partenaire pour relancer la creation");
            }

            User user = new User();
            user.setUsername(partenaire.getTelephone());
            user.setPassword(passwordEncoder.encode(MOT_DE_PASSE_PARTENAIRE_DEFAUT));
            user.setRoles("USER");
            user.setActive(true);
            user.setFirstConnexion(1);
            user.setCreation(Instant.now());
            user.setLastUpdate(Instant.now());
            user = userRepository.save(user);
            // Historise le mot de passe INITIAL (par defaut) - meme raison que dans UserProfileService.create().
            passwordHistoryService.record(user.getId(), user.getPassword());

            Profile profile = Profile.builder()
                    .code(codeGeneratorService.generate(CodePrefix.PROFILE))
                    .firstname(partenaire.getPrenoms())
                    .lastname(partenaire.getNom())
                    .phone(partenaire.getTelephone())
                    .role("USER")
                    .user(user)
                    .active(true)
                    .expiration(false)
                    .build();
            profile = profileRepository.save(profile);

            boolean notifie = smsNotificationService.sendFromTemplate(
                    partenaire.getTelephone(),
                    TEMPLATE_BIENVENUE_PARTENAIRE,
                    Map.of(
                            "username", partenaire.getTelephone(),
                            "telephone", partenaire.getTelephone(),
                            "motDePasse", MOT_DE_PASSE_PARTENAIRE_DEFAUT,
                            "nomPartenaire", partenaire.getPrenoms() + " " + partenaire.getNom(),
                            "lienApplication", lienConnexionPartenaire
                    ),
                    TypeMessageSms.bienvenue_partenaire
            );
            if (!notifie) {
                log.warn("Compte partenaire cree pour {} mais l'envoi du SMS de bienvenue a echoue", partenaire.getTelephone());
            }

            return new ResultatCreationCompte(true, profile, null);
        } catch (RuntimeException e) {
            log.error("Echec inattendu de la creation automatique de compte pour le partenaire {}", partenaire.getCodePartenaire(), e);
            return new ResultatCreationCompte(false, null, "Erreur technique lors de la creation du compte, veuillez reessayer");
        }
    }

    private record ResultatCreationCompte(boolean succes, Profile profile, String erreur) {
    }

    /**
     * compte cree/actif <=> typeProfile.declencheCreationCompte ET partenaire.accesActiviteCommerciale
     * (contrat "Frequence de paiement de commission" §2.1).
     */
    private boolean estEligibleCreationCompte(Partenaire partenaire) {
        TypeProfile typeProfile = partenaire.getTypeProfile();
        return typeProfile != null && typeProfile.isDeclencheCreationCompte() && partenaire.isAccesActiviteCommerciale();
    }

    /**
     * Quand un compte est deja lie au partenaire, aligne son etat actif/inactif sur l'eligibilite
     * courante - desactivation en cascade (User + Profile, memes effets que PATCH /users/{id}/status,
     * y compris la revocation des sessions actives) si le partenaire n'est plus eligible, reactivation
     * symetrique s'il le redevient (contrat §2.4, decisions tranchees le 2026-08-14). No-op si l'etat
     * du compte correspond deja a l'eligibilite (evite un save() et un appel a userProfileService inutiles
     * a chaque update()).
     */
    private void synchroniserActivationCompte(Partenaire partenaire, boolean eligible) {
        Profile profile = partenaire.getProfile();
        if (profile.isActive() == eligible) {
            return;
        }
        userProfileService.setActive(profile.getUser().getId(), new UserStatusRequest(eligible));
        profile.setActive(eligible);
        profileRepository.save(profile);
    }

    private void validateObjectifProduction(PartenaireType type, BigDecimal objectifProduction) {
        if (type != PartenaireType.APPORTEUR && objectifProduction == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "objectifProduction est obligatoire pour ce type de partenaire (optionnel uniquement pour APPORTEUR)");
        }
    }

    /**
     * Supprime le partenaire ET, s'il en a un, son compte de connexion associe (User+Profile+sessions,
     * via UserProfileService.delete() - meme suppression que DELETE /users/{id}). Sans ca, supprimer un
     * partenaire laissait un compte orphelin, toujours actif, sans plus aucun partenaire rattache (bug
     * constate le 2026-08-17, cf. memoire "login-response-partenaire-id"). Le partenaire est supprime
     * AVANT le compte pour ne pas violer la contrainte de cle etrangere Partenaire.profile_id.
     */
    /**
     * Endpoint leger pour ne modifier QUE l'objectif de production, sans repasser par le PUT complet
     * (contrat "Endpoint leger pour modifier uniquement l'objectif d'un partenaire", 2026-08-30) - meme
     * pattern que ExerciceComptableService.definirObjectifGlobal(). Pas de garde de cloture ici :
     * contrairement a l'exercice comptable, le Partenaire n'a pas son propre etat verrouille.
     */
    @Transactional
    public PartenaireResponse definirObjectifProduction(Long id, BigDecimal objectifProduction) {
        requireAdmin();
        Partenaire partenaire = getPartenaire(id);
        partenaire.setObjectifProduction(objectifProduction);
        return toResponse(repository.save(partenaire), null);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        Partenaire partenaire = getPartenaire(id);
        Profile profile = partenaire.getProfile();
        repository.delete(partenaire);
        if (profile != null) {
            userProfileService.delete(profile.getUser().getId());
        }
    }

    private Partenaire getPartenaire(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + id));
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + userId));
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElse(user.getUsername());
    }

    private PartenaireDocumentResponse toDocumentResponse(PartenaireDocument document) {
        return new PartenaireDocumentResponse(
                document.getId(),
                document.getPartenaire() == null ? null : document.getPartenaire().getId(),
                document.getType(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                displayName(document.getUploadedBy()),
                document.getCreatedAt(),
                document.getDateExpiration(),
                document.getDateExpiration() != null && document.getDateExpiration().isBefore(java.time.LocalDate.now()),
                document.getStatutValidation() == null ? ci.assurzen.entities.StatutValidationDocument.EN_ATTENTE : document.getStatutValidation(),
                document.getMotifRefus(),
                document.getValideLe(),
                document.getValidePar() == null ? null : displayName(document.getValidePar())
        );
    }

    /**
     * Comme resolveProfile, avec en plus un garde-fou : refuse de rattacher un Profile deja lie a un
     * AUTRE Partenaire (excludingPartenaireId = le partenaire en cours de modification, pour ne pas se
     * bloquer soi-meme sur un update qui renvoie le meme profileId deja assigne). Sans ce garde-fou,
     * rien n'empechait plusieurs Partenaire de partager le meme Profile - situation reelle constatee
     * le 2026-08-21 (4 partenaires de test lies au meme profil), qui faisait planter le login
     * (AuthService.buildUserResponse() via findByProfile_Id) avec IncorrectResultSizeDataAccessException.
     */
    private Profile resolveProfileUnique(Long profileId, Long excludingPartenaireId) {
        if (profileId == null) {
            return null;
        }
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile introuvable: " + profileId));

        boolean dejaLieAUnAutrePartenaire = repository.findByProfile_Id(profileId).stream()
                .anyMatch(autre -> !autre.getId().equals(excludingPartenaireId));
        if (dejaLieAUnAutrePartenaire) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce profil est deja rattache a un autre partenaire: " + profileId);
        }

        // Garde-fou symetrique a UserProfileService.rejeterSiRoleCommercialSurCompteRattacheAPartenaire() :
        // le frontend du module Commercial bascule son interface sur le seul signal "roles contient
        // COMMERCIAL" pour exclure un compte partenaire (cf. module-commercial-retour-frontend.md §2).
        User utilisateur = profile.getUser();
        if (utilisateur != null && utilisateur.getRoles() != null
                && utilisateur.getRoles().toUpperCase(java.util.Locale.ROOT).contains("COMMERCIAL")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce profil appartient a un compte commercial interne, il ne peut pas etre rattache a un partenaire: " + profileId);
        }

        return profile;
    }

    private TypeProfile resolveTypeProfile(Long typeProfileId) {
        if (typeProfileId == null) {
            return null;
        }
        return typeProfileRepository.findById(typeProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TypeProfile introuvable: " + typeProfileId));
    }

    private ExerciceComptable resolveExerciceComptable(Long exerciceComptableId) {
        if (exerciceComptableId == null) {
            return null;
        }
        return exerciceComptableRepository.findById(exerciceComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice comptable introuvable: " + exerciceComptableId));
    }

    private VillePartenaire resolveVillePartenaire(Long villePartenaireId) {
        if (villePartenaireId == null) {
            return null;
        }
        return villePartenaireRepository.findById(villePartenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ville introuvable: " + villePartenaireId));
    }

    private PartenaireResponse toResponse(Partenaire partenaire) {
        return toResponse(partenaire, null);
    }

    private void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    /**
     * IDOR confirme en live le 2026-08-23 (audit de securite) sur GET /partenaires/{id} : n'importe
     * quel compte partenaire pouvait consulter le profil complet (nom, prenoms, telephone, et surtout
     * numeroWaveCommission) de N'IMPORTE QUEL AUTRE partenaire, id sequentiel facilement enumerable,
     * aucun controle d'acces avant ce correctif. Rejette AVANT la verification d'existence (getPartenaire)
     * pour qu'un compte partenaire ne puisse jamais distinguer, via 403 vs 404, un {id} existant d'un
     * {id} inexistant. Les comptes SANS lien partenaire (commercial interne, admin) ne sont pas
     * concernes - la lecture generale leur reste ouverte (deja tranche le 2026-08-23, ecriture
     * seule restreinte aux admins).
     */
    private void verifierAccesPartenaireCible(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        List<Partenaire> partenairesLies = profileRepository.findByUserId(principal.userId())
                .map(profile -> repository.findByProfile_Id(profile.getId()))
                .orElse(List.of());
        if (partenairesLies.isEmpty()) {
            return;
        }
        boolean estCePartenaire = partenairesLies.stream().anyMatch(p -> p.getId().equals(partenaireId));
        if (!estCePartenaire) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    /**
     * numeroWaveCommission - cible de fraude la plus sensible du module (audit de securite
     * pre-production du 2026-08-23) - n'est visible qu'a l'admin ou au partenaire consultant SA
     * PROPRE fiche, jamais a un autre compte authentifie qui listerait/consulterait un partenaire
     * quelconque (faille reelle confirmee : GET /partenaires/{id} renvoyait ce numero a n'importe
     * qui). Le reste de PartenaireResponse reste inchange (lecture generale toujours autorisee).
     */
    private String numeroWaveVisible(Partenaire partenaire) {
        AuthenticatedUserPrincipal principal;
        try {
            principal = authService.currentPrincipal();
        } catch (RuntimeException e) {
            return null;
        }
        if (isAdmin(principal)) {
            return partenaire.getNumeroWaveCommission();
        }
        boolean estCePartenaire = partenaire.getProfile() != null
                && partenaire.getProfile().getUser() != null
                && partenaire.getProfile().getUser().getId().equals(principal.userId());
        return estCePartenaire ? partenaire.getNumeroWaveCommission() : null;
    }

    private PartenaireResponse toResponse(Partenaire partenaire, ResultatCreationCompte resultatCreationCompte) {
        BigDecimal objectifRealise = computeObjectifRealise(partenaire);
        BigDecimal tauxRealisation = computeTauxRealisation(objectifRealise, partenaire.getObjectifProduction());
        ExerciceComptable exercice = partenaire.getExerciceComptable();
        VillePartenaire ville = partenaire.getVillePartenaire();
        TypeProfile typeProfile = partenaire.getTypeProfile();

        return new PartenaireResponse(
                partenaire.getId(),
                partenaire.getCodePartenaire(),
                partenaire.getType(),
                partenaire.getNom(),
                partenaire.getPrenoms(),
                partenaire.getObjectifProduction(),
                objectifRealise,
                tauxRealisation,
                partenaire.getProfile() == null ? null : partenaire.getProfile().getId(),
                exercice == null ? null : exercice.getId(),
                exercice == null ? null : exercice.getLibelle(),
                ville == null ? null : ville.getId(),
                ville == null ? null : ville.getNomVille(),
                partenaire.isActive(),
                partenaire.getCreation(),
                partenaire.getLastUpdate(),
                typeProfile == null ? null : typeProfile.getId(),
                partenaire.getTelephone(),
                numeroWaveVisible(partenaire),
                resultatCreationCompte == null ? null : resultatCreationCompte.succes(),
                resultatCreationCompte == null ? null : resultatCreationCompte.erreur(),
                partenaire.getFrequencePaiementCommission(),
                partenaire.isAccesActiviteCommerciale(),
                partenaire.isPrimeObjectifActivee(),
                partenaire.getId() == null ? 0L : partenaireDocumentRepository.countEnAttente(partenaire.getId())
        );
    }

    /**
     * Somme des primes des contrats signes du partenaire (seuls les contrats signed comptent
     * comme production reellement realisee, coherent avec l'eligibilite des commissions).
     * Si un exercice comptable est assigne au partenaire, seuls les contrats signes durant
     * la periode de cet exercice sont pris en compte (l'objectif est annuel/par exercice).
     * A defaut d'exercice assigne, on retombe sur la somme non filtree (comportement precedent).
     */
    /** Public : reutilise telle quelle par PrimeObjectifService pour calculer la base de la prime
     * d'objectif (contrat "Prime d'objectif partenaire", 2026-08-28) - une seule source de verite pour
     * ce calcul, evite un ecart entre le taux affiche sur la fiche partenaire et celui utilise pour la prime. */
    public BigDecimal computeObjectifRealise(Partenaire partenaire) {
        ExerciceComptable exercice = partenaire.getExerciceComptable();

        return contractRepository.findByStatusAndPartenaireId(ContractStatus.signed, partenaire.getId()).stream()
                .filter(contract -> exercice == null || isSignedWithinExercice(contract, exercice))
                .map(Contract::getPremium)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isSignedWithinExercice(Contract contract, ExerciceComptable exercice) {
        LocalDate signedAt = contract.getSignedAt();
        if (signedAt == null) {
            return false;
        }
        return !signedAt.isBefore(exercice.getDateDebut()) && !signedAt.isAfter(exercice.getDateFin());
    }

    /** Public, meme raison que computeObjectifRealise() ci-dessus. */
    public BigDecimal computeTauxRealisation(BigDecimal objectifRealise, BigDecimal objectifProduction) {
        if (objectifProduction == null || objectifProduction.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return objectifRealise
                .divide(objectifProduction, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
