package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ReclamationAffectationRequest;
import ci.assurzen.dto.ReclamationClotureRequest;
import ci.assurzen.dto.ReclamationCommentaireRequest;
import ci.assurzen.dto.ReclamationCommentaireResponse;
import ci.assurzen.dto.ReclamationPieceJointeContent;
import ci.assurzen.dto.ReclamationPieceJointeResponse;
import ci.assurzen.dto.ReclamationRequest;
import ci.assurzen.dto.ReclamationResponse;
import ci.assurzen.dto.ReclamationStatistiquesResponse;
import ci.assurzen.dto.ReclamationStatutRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.EnqueteContexte;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.Reclamation;
import ci.assurzen.entities.ReclamationCanal;
import ci.assurzen.entities.ReclamationCommentaire;
import ci.assurzen.entities.ReclamationPieceJointe;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.ReclamationStatut;
import ci.assurzen.entities.ReclamationType;
import ci.assurzen.entities.Sinistre;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.ReclamationCommentaireRepository;
import ci.assurzen.repositories.ReclamationPieceJointeRepository;
import ci.assurzen.repositories.ReclamationRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import static ci.assurzen.utils.SafeAccess.safe;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReclamationService {

    private static final Map<ReclamationStatut, Set<ReclamationStatut>> TRANSITIONS = new EnumMap<>(ReclamationStatut.class);

    static {
        TRANSITIONS.put(ReclamationStatut.NOUVELLE, Set.of(ReclamationStatut.EN_ANALYSE, ReclamationStatut.AFFECTEE, ReclamationStatut.ANNULEE));
        TRANSITIONS.put(ReclamationStatut.EN_ANALYSE, Set.of(ReclamationStatut.AFFECTEE, ReclamationStatut.EN_COURS, ReclamationStatut.ANNULEE));
        TRANSITIONS.put(ReclamationStatut.AFFECTEE, Set.of(ReclamationStatut.EN_COURS, ReclamationStatut.ANNULEE));
        TRANSITIONS.put(ReclamationStatut.EN_COURS, Set.of(ReclamationStatut.EN_ATTENTE_CLIENT, ReclamationStatut.RESOLUE, ReclamationStatut.ANNULEE));
        TRANSITIONS.put(ReclamationStatut.EN_ATTENTE_CLIENT, Set.of(ReclamationStatut.EN_COURS, ReclamationStatut.RESOLUE, ReclamationStatut.ANNULEE));
        TRANSITIONS.put(ReclamationStatut.RESOLUE, Set.of(ReclamationStatut.EN_COURS));
        TRANSITIONS.put(ReclamationStatut.FERMEE, Set.of());
        TRANSITIONS.put(ReclamationStatut.ANNULEE, Set.of());
    }

    private final ReclamationRepository reclamationRepository;
    private final ReclamationCommentaireRepository reclamationCommentaireRepository;
    private final ReclamationPieceJointeRepository reclamationPieceJointeRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final ProduitRepository produitRepository;
    private final SinistreRepository sinistreRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final AuthService authService;
    private final CodeGeneratorService codeGeneratorService;
    private final ReclamationStorageService reclamationStorageService;
    private final SmsNotificationService smsNotificationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final DynamicConfigurationService dynamicConfigurationService;

    public PageResponse<ReclamationResponse> search(ReclamationStatut statut, ReclamationType type, ReclamationPriorite priorite,
                                                      ReclamationCanal canal, Long gestionnaireId, Long insuredId,
                                                      Integer page, Integer size) {
        return paginate(filter(statut, type, priorite, canal, gestionnaireId, insuredId), page, size);
    }

    public ReclamationResponse get(Long id) {
        return toResponse(getScoped(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "RECLAMATIONS", entityName = "Reclamation")
    public ReclamationResponse create(ReclamationRequest request) {
        Insured insured = resolveInsured(request.insuredId());
        checkAccess(insured);

        Reclamation reclamation = Reclamation.builder()
                .numero(codeGeneratorService.generate(CodePrefix.RECLAMATION))
                .insured(insured)
                .contrat(request.contratId() != null ? resolveContract(request.contratId()) : null)
                .produit(request.produitId() != null ? resolveProduit(request.produitId()) : null)
                .sinistre(request.sinistreId() != null ? resolveSinistre(request.sinistreId()) : null)
                .canal(request.canal())
                .type(request.type())
                .priorite(request.priorite() != null ? request.priorite() : ReclamationPriorite.NORMALE)
                .statut(ReclamationStatut.NOUVELLE)
                .description(request.description())
                .creeeAutomatiquement(false)
                .build();
        reclamation = reclamationRepository.save(reclamation);

        boolean notifie = smsNotificationService.sendReclamationCreatedSms(insured.getClientPhone(), reclamation.getNumero());
        reclamation.setNotificationEnvoyee(notifie);
        reclamation = reclamationRepository.save(reclamation);

        return toResponse(reclamation);
    }

    @Transactional
    public ReclamationResponse update(Long id, ReclamationRequest request) {
        Reclamation reclamation = getScoped(id);
        ReclamationResponse avant = toResponse(reclamation);

        reclamation.setContrat(request.contratId() != null ? resolveContract(request.contratId()) : null);
        reclamation.setProduit(request.produitId() != null ? resolveProduit(request.produitId()) : null);
        reclamation.setSinistre(request.sinistreId() != null ? resolveSinistre(request.sinistreId()) : null);
        reclamation.setCanal(request.canal());
        reclamation.setType(request.type());
        if (request.priorite() != null) {
            reclamation.setPriorite(request.priorite());
        }
        reclamation.setDescription(request.description());

        ReclamationResponse apres = toResponse(reclamationRepository.save(reclamation));
        functionalAuditRecorder.recordUpdate("RECLAMATIONS", "Reclamation", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    public ReclamationResponse affecter(Long id, ReclamationAffectationRequest request) {
        requireCanManage();
        Reclamation reclamation = getScoped(id);
        ReclamationResponse avant = toResponse(reclamation);

        reclamation.setGestionnaire(resolveUser(request.gestionnaireId()));
        reclamation.setService(request.service());
        reclamation.setDateAffectation(Instant.now());
        if (reclamation.getStatut() == ReclamationStatut.NOUVELLE || reclamation.getStatut() == ReclamationStatut.EN_ANALYSE) {
            reclamation.setStatut(ReclamationStatut.AFFECTEE);
        }
        reclamation = reclamationRepository.save(reclamation);

        boolean notifie = smsNotificationService.sendReclamationSuiviSms(safe(reclamation.getInsured()::getClientPhone),
                reclamation.getNumero(), "votre dossier a ete affecte a un gestionnaire et est en cours de traitement.");
        reclamation.setNotificationEnvoyee(notifie);
        reclamation = reclamationRepository.save(reclamation);

        ReclamationResponse apres = toResponse(reclamation);
        functionalAuditRecorder.recordUpdate("RECLAMATIONS", "Reclamation", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    public ReclamationResponse changerStatut(Long id, ReclamationStatutRequest request) {
        requireCanManage();
        if (request.statut() == ReclamationStatut.FERMEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cloture d'une reclamation passe par POST /reclamations/{id}/cloture");
        }
        Reclamation reclamation = getScoped(id);
        ReclamationResponse avant = toResponse(reclamation);
        validerTransition(reclamation.getStatut(), request.statut());

        reclamation.setStatut(request.statut());
        if (request.statut() == ReclamationStatut.RESOLUE && reclamation.getDateResolution() == null) {
            reclamation.setDateResolution(Instant.now());
        }
        reclamation = reclamationRepository.save(reclamation);

        if (StringUtils.hasText(request.commentaire())) {
            enregistrerCommentaire(reclamation, "Changement de statut -> " + request.statut() + " : " + request.commentaire());
        }

        String detail = "nouveau statut = " + request.statut()
                + (StringUtils.hasText(request.commentaire()) ? ". " + request.commentaire() : "");
        boolean notifie = smsNotificationService.sendReclamationSuiviSms(safe(reclamation.getInsured()::getClientPhone),
                reclamation.getNumero(), detail);
        reclamation.setNotificationEnvoyee(notifie);
        reclamation = reclamationRepository.save(reclamation);

        ReclamationResponse apres = toResponse(reclamation);
        functionalAuditRecorder.recordUpdate("RECLAMATIONS", "Reclamation", String.valueOf(id), avant, apres, request.commentaire());
        return apres;
    }

    @Transactional
    public ReclamationResponse cloturer(Long id, ReclamationClotureRequest request) {
        requireCanManage();
        Reclamation reclamation = getScoped(id);
        ReclamationResponse avant = toResponse(reclamation);
        if (reclamation.getStatut() != ReclamationStatut.RESOLUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une reclamation RESOLUE peut etre cloturee");
        }

        reclamation.setStatut(ReclamationStatut.FERMEE);
        reclamation.setDateCloture(Instant.now());
        reclamation.setMotifCloture(request.motif());
        reclamation = reclamationRepository.save(reclamation);

        enregistrerCommentaire(reclamation, "Cloture : " + request.motif());

        boolean notifie = smsNotificationService.sendReclamationSuiviSms(safe(reclamation.getInsured()::getClientPhone),
                reclamation.getNumero(), "votre dossier a ete cloture. " + request.motif());
        reclamation.setNotificationEnvoyee(notifie);
        reclamation = reclamationRepository.save(reclamation);

        ReclamationResponse apres = toResponse(reclamation);
        functionalAuditRecorder.recordUpdate("RECLAMATIONS", "Reclamation", String.valueOf(id), avant, apres, request.motif());
        return apres;
    }

    /**
     * Suppression physique, volontairement restreinte aux reclamations sans historique metier
     * significatif (cf. docs/api-contracts/reclamations-delete.md) : reservee au meme role que le
     * reste de la gestion des reclamations (requireCanManage, pas un ADMIN-only nouveau qui serait
     * incoherent avec affectation/statut/cloture ci-dessus), et refusee des qu'un commentaire, une
     * piece jointe, ou une creation automatique (score NPS bas - un vrai signal metier, pas un test)
     * existe. Objectif : purger un enregistrement de test/doublon, pas contourner la cloture pour un
     * vrai dossier deja engage.
     */
    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "RECLAMATIONS", entityName = "Reclamation")
    public void delete(Long id) {
        requireCanManage();
        Reclamation reclamation = getScoped(id);

        if (reclamation.isCreeeAutomatiquement()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer une reclamation creee automatiquement (score NPS bas) : cloturez-la plutot");
        }
        if (reclamationCommentaireRepository.existsByReclamationId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer une reclamation ayant des commentaires : cloturez-la plutot");
        }
        if (reclamationPieceJointeRepository.existsByReclamationId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer une reclamation ayant des pieces jointes : cloturez-la plutot");
        }

        reclamationRepository.delete(reclamation);
    }

    public List<ReclamationCommentaireResponse> listCommentaires(Long id) {
        getScoped(id);
        return reclamationCommentaireRepository.findByReclamationIdOrderByCreatedAtAsc(id).stream()
                .map(this::toCommentaireResponse)
                .toList();
    }

    @Transactional
    public ReclamationCommentaireResponse ajouterCommentaire(Long id, ReclamationCommentaireRequest request) {
        Reclamation reclamation = getScoped(id);
        return toCommentaireResponse(enregistrerCommentaire(reclamation, request.contenu()));
    }

    public List<ReclamationPieceJointeResponse> listPiecesJointes(Long id) {
        getScoped(id);
        return reclamationPieceJointeRepository.findByReclamationIdOrderByCreatedAtAsc(id).stream()
                .map(this::toPieceJointeResponse)
                .toList();
    }

    @Transactional
    public ReclamationPieceJointeResponse ajouterPieceJointe(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        long maxFileSize = dynamicConfigurationService.getLong("MAX_FILE_SIZE", 10L * 1024 * 1024);
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier trop volumineux (" + file.getSize() + " octets, maximum autorise: " + maxFileSize + " octets, configurable via MAX_FILE_SIZE)");
        }
        Reclamation reclamation = getScoped(id);
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();

        String storedPath;
        try {
            storedPath = reclamationStorageService.store(id, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ReclamationPieceJointe piece = ReclamationPieceJointe.builder()
                .reclamation(reclamation)
                .fileName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "piece-jointe")
                .storedPath(storedPath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedBy(resolveUser(principal.userId()))
                .build();

        return toPieceJointeResponse(reclamationPieceJointeRepository.save(piece));
    }

    public ReclamationPieceJointeContent getPieceJointeContent(Long id, Long pieceId) {
        getScoped(id);
        ReclamationPieceJointe piece = reclamationPieceJointeRepository.findByIdAndReclamationId(pieceId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Piece jointe introuvable: " + pieceId));
        return new ReclamationPieceJointeContent(reclamationStorageService.read(piece.getStoredPath()), piece.getContentType(), piece.getFileName());
    }

    public ReclamationStatistiquesResponse statistiques() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean canViewAll = FidelisationRoles.canViewAll(principal);

        List<Reclamation> reclamations = reclamationRepository.findAll().stream()
                .filter(r -> canViewAll || estAccessible(r, principal))
                .toList();

        Map<ReclamationStatut, Long> parStatut = reclamations.stream()
                .collect(Collectors.groupingBy(Reclamation::getStatut, () -> new EnumMap<>(ReclamationStatut.class), Collectors.counting()));
        Map<ReclamationPriorite, Long> parPriorite = reclamations.stream()
                .collect(Collectors.groupingBy(Reclamation::getPriorite, () -> new EnumMap<>(ReclamationPriorite.class), Collectors.counting()));
        Map<ReclamationType, Long> parType = reclamations.stream()
                .collect(Collectors.groupingBy(Reclamation::getType, () -> new EnumMap<>(ReclamationType.class), Collectors.counting()));
        Map<ReclamationCanal, Long> parCanal = reclamations.stream()
                .collect(Collectors.groupingBy(Reclamation::getCanal, () -> new EnumMap<>(ReclamationCanal.class), Collectors.counting()));

        long ouvertes = reclamations.stream()
                .filter(r -> r.getStatut() != ReclamationStatut.FERMEE && r.getStatut() != ReclamationStatut.ANNULEE)
                .count();
        long resolues = parStatut.getOrDefault(ReclamationStatut.RESOLUE, 0L);
        long fermees = parStatut.getOrDefault(ReclamationStatut.FERMEE, 0L);
        long creeesAutomatiquement = reclamations.stream().filter(Reclamation::isCreeeAutomatiquement).count();

        List<Reclamation> resoluesAvecDelai = reclamations.stream()
                .filter(r -> r.getDateResolution() != null)
                .toList();
        Double delaiMoyenHeures = resoluesAvecDelai.isEmpty() ? null : resoluesAvecDelai.stream()
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getDateResolution()).toMinutes())
                .average()
                .orElse(0) / 60.0;

        return new ReclamationStatistiquesResponse(reclamations.size(), ouvertes, resolues, fermees, creeesAutomatiquement,
                delaiMoyenHeures, parStatut, parPriorite, parType, parCanal);
    }

    /**
     * Appele par SatisfactionService.repondre() : une reponse d'enquete "Detracteur" (score NPS <= 6)
     * declenche automatiquement une reclamation, sur le meme principe que les grands CRM (voir memo de
     * conception). Priorite HAUTE si score <= 3, NORMALE sinon. Ne se declenche jamais deux fois pour la
     * meme enquete car repondre() rejette deja toute deuxieme reponse (409) en amont.
     */
    @Transactional
    public void creerAutomatiquementDepuisEnquete(EnqueteSatisfaction enquete, int score, String commentaire) {
        if (score > 6 || enquete.getInsured() == null) {
            return;
        }

        String description = StringUtils.hasText(commentaire)
                ? "Creee automatiquement suite a une note de satisfaction basse (score " + score + "/10) : " + commentaire
                : "Creee automatiquement suite a une note de satisfaction basse (score " + score + "/10), sans commentaire.";

        Reclamation reclamation = Reclamation.builder()
                .numero(codeGeneratorService.generate(CodePrefix.RECLAMATION))
                .insured(enquete.getInsured())
                .contrat(enquete.getContrat())
                .sinistre(enquete.getSinistre())
                .enquete(enquete)
                .canal(ReclamationCanal.ENQUETE_SATISFACTION)
                .type(typeDepuisContexte(enquete.getContexte()))
                .priorite(score <= 3 ? ReclamationPriorite.HAUTE : ReclamationPriorite.NORMALE)
                .statut(ReclamationStatut.NOUVELLE)
                .description(description)
                .creeeAutomatiquement(true)
                .build();
        reclamation = reclamationRepository.save(reclamation);

        boolean notifie = smsNotificationService.sendReclamationCreatedSms(safe(reclamation.getInsured()::getClientPhone), reclamation.getNumero());
        reclamation.setNotificationEnvoyee(notifie);
        reclamationRepository.save(reclamation);

        functionalAuditRecorder.recordAction("RECLAMATIONS", "Reclamation", String.valueOf(reclamation.getId()), ActionAudit.CREATE, description);
    }

    private ReclamationType typeDepuisContexte(EnqueteContexte contexte) {
        if (contexte == null) {
            return ReclamationType.RELATION_CLIENT;
        }
        return switch (contexte) {
            case POST_SINISTRE -> ReclamationType.SINISTRE;
            case POST_SOUSCRIPTION -> ReclamationType.SOUSCRIPTION;
            case POST_RESILIATION -> ReclamationType.RELATION_CLIENT;
            case PERIODIQUE -> ReclamationType.SERVICE;
        };
    }

    private ReclamationCommentaire enregistrerCommentaire(Reclamation reclamation, String contenu) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        ReclamationCommentaire commentaire = ReclamationCommentaire.builder()
                .reclamation(reclamation)
                .contenu(contenu)
                .auteur(resolveUser(principal.userId()))
                .build();
        return reclamationCommentaireRepository.save(commentaire);
    }

    private void validerTransition(ReclamationStatut actuel, ReclamationStatut cible) {
        if (actuel == cible) {
            return;
        }
        Set<ReclamationStatut> autorises = TRANSITIONS.getOrDefault(actuel, Set.of());
        if (!autorises.contains(cible)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition " + actuel + " -> " + cible + " non autorisee");
        }
    }

    private void requireCanManage() {
        if (!FidelisationRoles.canManageReclamations(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux gestionnaires de reclamations");
        }
    }

    private Reclamation getScoped(Long id) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        Reclamation reclamation = reclamationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reclamation introuvable: " + id));
        if (FidelisationRoles.canViewAll(principal) || estAccessible(reclamation, principal)) {
            return reclamation;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reclamation introuvable: " + id);
    }

    private void checkAccess(Insured insured) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (FidelisationRoles.canViewAll(principal) || estAccessibleParClient(insured, principal)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + (insured == null ? null : insured.getId()));
    }

    private boolean estAccessible(Reclamation reclamation, AuthenticatedUserPrincipal principal) {
        if (reclamation.getGestionnaire() != null && reclamation.getGestionnaire().getId().equals(principal.userId())) {
            return true;
        }
        return estAccessibleParClient(reclamation.getInsured(), principal);
    }

    private boolean estAccessibleParClient(Insured insured, AuthenticatedUserPrincipal principal) {
        if (insured == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .anyMatch(c -> c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId()));
    }

    private List<ReclamationResponse> filter(ReclamationStatut statut, ReclamationType type, ReclamationPriorite priorite,
                                              ReclamationCanal canal, Long gestionnaireId, Long insuredId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean canViewAll = FidelisationRoles.canViewAll(principal);

        return reclamationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(r -> canViewAll || estAccessible(r, principal))
                .filter(r -> statut == null || r.getStatut() == statut)
                .filter(r -> type == null || r.getType() == type)
                .filter(r -> priorite == null || r.getPriorite() == priorite)
                .filter(r -> canal == null || r.getCanal() == canal)
                .filter(r -> gestionnaireId == null || (r.getGestionnaire() != null && gestionnaireId.equals(r.getGestionnaire().getId())))
                .filter(r -> insuredId == null || (r.getInsured() != null && insuredId.equals(r.getInsured().getId())))
                .map(this::toResponse)
                .toList();
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId));
    }

    private Contract resolveContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));
    }

    private Produit resolveProduit(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + produitId));
    }

    private Sinistre resolveSinistre(Long sinistreId) {
        return sinistreRepository.findById(sinistreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinistre introuvable: " + sinistreId));
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
                .orElseGet(() -> safe(user::getUsername));
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return safe(() -> insured.getPrenoms() + " " + insured.getNom());
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

    private ReclamationResponse toResponse(Reclamation r) {
        return new ReclamationResponse(
                r.getId(),
                r.getNumero(),
                r.getInsured() == null ? null : safe(r.getInsured()::getId),
                insuredName(r.getInsured()),
                r.getContrat() == null ? null : safe(r.getContrat()::getId),
                r.getContrat() == null ? null : safe(r.getContrat()::getPolicyNumber),
                r.getProduit() == null ? null : safe(r.getProduit()::getId),
                r.getProduit() == null ? null : safe(r.getProduit()::getNomProduit),
                r.getSinistre() == null ? null : r.getSinistre().getId(),
                r.getSinistre() == null ? null : r.getSinistre().getNumero(),
                r.getEnquete() == null ? null : r.getEnquete().getId(),
                r.getEnquete() == null ? null : r.getEnquete().getCode(),
                r.getCanal(),
                r.getType(),
                r.getPriorite(),
                r.getStatut(),
                r.getDescription(),
                r.getGestionnaire() == null ? null : safe(r.getGestionnaire()::getId),
                displayName(r.getGestionnaire()),
                r.getService(),
                r.getDateAffectation(),
                r.getDateResolution(),
                r.getDateCloture(),
                r.getMotifCloture(),
                r.isCreeeAutomatiquement(),
                r.getNotificationEnvoyee(),
                r.getCreatedAt(),
                r.getLastUpdate()
        );
    }

    private ReclamationCommentaireResponse toCommentaireResponse(ReclamationCommentaire c) {
        return new ReclamationCommentaireResponse(
                c.getId(),
                c.getReclamation() == null ? null : c.getReclamation().getId(),
                c.getContenu(),
                c.getAuteur() == null ? null : safe(c.getAuteur()::getId),
                displayName(c.getAuteur()),
                c.getCreatedAt()
        );
    }

    private ReclamationPieceJointeResponse toPieceJointeResponse(ReclamationPieceJointe p) {
        return new ReclamationPieceJointeResponse(
                p.getId(),
                p.getReclamation() == null ? null : p.getReclamation().getId(),
                p.getFileName(),
                p.getContentType(),
                p.getSizeBytes(),
                displayName(p.getUploadedBy()),
                p.getCreatedAt()
        );
    }
}
