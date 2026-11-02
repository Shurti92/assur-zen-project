package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CrossSellAffectationRequest;
import ci.assurzen.dto.CrossSellHistoryResponse;
import ci.assurzen.dto.CrossSellOpportunityRequest;
import ci.assurzen.dto.CrossSellOpportunityResponse;
import ci.assurzen.dto.CrossSellStatutRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.CrossSellEvenementType;
import ci.assurzen.entities.CrossSellHistory;
import ci.assurzen.entities.CrossSellOpportunity;
import ci.assurzen.entities.CrossSellStatut;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.CrossSellHistoryRepository;
import ci.assurzen.repositories.CrossSellOpportunityRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrossSellOpportunityService {

    private static final Map<CrossSellStatut, Set<CrossSellStatut>> TRANSITIONS = new EnumMap<>(CrossSellStatut.class);
    private static final Set<CrossSellStatut> STATUTS_TERMINAUX =
            EnumSet.of(CrossSellStatut.REFUSEE, CrossSellStatut.CONVERTIE, CrossSellStatut.ANNULEE, CrossSellStatut.EXPIREE);

    static {
        TRANSITIONS.put(CrossSellStatut.NOUVELLE, Set.of(CrossSellStatut.QUALIFIEE, CrossSellStatut.AFFECTEE, CrossSellStatut.ANNULEE));
        TRANSITIONS.put(CrossSellStatut.QUALIFIEE, Set.of(CrossSellStatut.AFFECTEE, CrossSellStatut.ANNULEE));
        TRANSITIONS.put(CrossSellStatut.AFFECTEE, Set.of(CrossSellStatut.EN_COURS, CrossSellStatut.ANNULEE));
        TRANSITIONS.put(CrossSellStatut.EN_COURS, Set.of(CrossSellStatut.ACCEPTEE, CrossSellStatut.REFUSEE, CrossSellStatut.ANNULEE));
        TRANSITIONS.put(CrossSellStatut.ACCEPTEE, Set.of(CrossSellStatut.CONVERTIE, CrossSellStatut.ANNULEE));
        TRANSITIONS.put(CrossSellStatut.REFUSEE, Set.of());
        TRANSITIONS.put(CrossSellStatut.CONVERTIE, Set.of());
        TRANSITIONS.put(CrossSellStatut.EXPIREE, Set.of());
        TRANSITIONS.put(CrossSellStatut.ANNULEE, Set.of());
    }

    private final CrossSellOpportunityRepository crossSellOpportunityRepository;
    private final CrossSellHistoryRepository crossSellHistoryRepository;
    private final CrossSellRuleEvaluator crossSellRuleEvaluator;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final ProduitRepository produitRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final AuthService authService;
    private final CodeGeneratorService codeGeneratorService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<CrossSellOpportunityResponse> search(CrossSellStatut statut, ReclamationPriorite priorite,
                                                               Long commercialId, Long insuredId, Integer page, Integer size) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean canViewAll = FidelisationRoles.canViewAll(principal);

        List<CrossSellOpportunityResponse> items = crossSellOpportunityRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(o -> canViewAll || estAccessible(o, principal))
                .filter(o -> statut == null || o.getStatut() == statut)
                .filter(o -> priorite == null || o.getPriorite() == priorite)
                .filter(o -> commercialId == null || (o.getCommercial() != null && commercialId.equals(o.getCommercial().getId())))
                .filter(o -> insuredId == null || (o.getInsured() != null && insuredId.equals(o.getInsured().getId())))
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public CrossSellOpportunityResponse get(Long id) {
        return toResponse(getScoped(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "CROSS_SELL", entityName = "CrossSellOpportunity")
    public CrossSellOpportunityResponse create(CrossSellOpportunityRequest request) {
        Insured insured = resolveInsured(request.insuredId());
        checkAccess(insured);

        CrossSellOpportunity opportunite = CrossSellOpportunity.builder()
                .code(codeGeneratorService.generate(CodePrefix.CROSS_SELL_OPPORTUNITY))
                .insured(insured)
                .contrat(request.contratId() != null ? resolveContract(request.contratId()) : null)
                .produitActuel(request.produitActuelId() != null ? resolveProduit(request.produitActuelId()) : null)
                .produitRecommande(resolveProduit(request.produitRecommandeId()))
                .score(request.score())
                .priorite(request.priorite() != null ? request.priorite() : ReclamationPriorite.NORMALE)
                .statut(CrossSellStatut.NOUVELLE)
                .commentaire(request.commentaire())
                .build();
        opportunite = crossSellOpportunityRepository.save(opportunite);

        enregistrerHistorique(opportunite, CrossSellEvenementType.CREATION, "Opportunite creee manuellement");

        return toResponse(opportunite);
    }

    /** Lance le moteur de detection pour un assure donne (ou tous les assures si insuredId est null). */
    @Transactional
    public List<CrossSellOpportunityResponse> generer(Long insuredId) {
        requireCanManage();
        List<Insured> cibles = insuredId != null ? List.of(resolveInsured(insuredId)) : insuredRepository.findAll();

        return cibles.stream()
                .flatMap(insured -> crossSellRuleEvaluator.genererPour(insured).stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CrossSellOpportunityResponse affecter(Long id, CrossSellAffectationRequest request) {
        requireCanManage();
        CrossSellOpportunity opportunite = getScoped(id);
        CrossSellOpportunityResponse avant = toResponse(opportunite);

        opportunite.setCommercial(resolveUser(request.commercialId()));
        opportunite.setDateAffectation(java.time.Instant.now());
        if (opportunite.getStatut() == CrossSellStatut.NOUVELLE || opportunite.getStatut() == CrossSellStatut.QUALIFIEE) {
            opportunite.setStatut(CrossSellStatut.AFFECTEE);
        }
        opportunite = crossSellOpportunityRepository.save(opportunite);

        enregistrerHistorique(opportunite, CrossSellEvenementType.AFFECTATION,
                "Affectee a " + displayName(opportunite.getCommercial()));

        CrossSellOpportunityResponse apres = toResponse(opportunite);
        functionalAuditRecorder.recordUpdate("CROSS_SELL", "CrossSellOpportunity", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    public CrossSellOpportunityResponse changerStatut(Long id, CrossSellStatutRequest request) {
        requireCanManage();
        CrossSellOpportunity opportunite = getScoped(id);
        CrossSellOpportunityResponse avant = toResponse(opportunite);
        validerTransition(opportunite.getStatut(), request.statut());

        opportunite.setStatut(request.statut());
        opportunite.setDateTraitement(java.time.Instant.now());
        if (StringUtils.hasText(request.motif())) {
            opportunite.setMotif(request.motif());
        }
        if (STATUTS_TERMINAUX.contains(request.statut())) {
            opportunite.setDateCloture(java.time.Instant.now());
        }
        opportunite = crossSellOpportunityRepository.save(opportunite);

        CrossSellEvenementType typeEvenement = switch (request.statut()) {
            case ACCEPTEE -> CrossSellEvenementType.ACCEPTATION;
            case REFUSEE -> CrossSellEvenementType.REFUS;
            case CONVERTIE -> CrossSellEvenementType.CONVERSION;
            case ANNULEE -> CrossSellEvenementType.ANNULATION;
            default -> CrossSellEvenementType.CHANGEMENT_STATUT;
        };
        enregistrerHistorique(opportunite, typeEvenement, "Statut -> " + request.statut()
                + (StringUtils.hasText(request.motif()) ? " : " + request.motif() : ""));

        CrossSellOpportunityResponse apres = toResponse(opportunite);
        functionalAuditRecorder.recordUpdate("CROSS_SELL", "CrossSellOpportunity", String.valueOf(id), avant, apres, request.motif());
        return apres;
    }

    public List<CrossSellHistoryResponse> historique(Long id) {
        getScoped(id);
        return crossSellHistoryRepository.findByOpportuniteIdOrderByCreatedAtAsc(id).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private void enregistrerHistorique(CrossSellOpportunity opportunite, CrossSellEvenementType type, String description) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        CrossSellHistory evenement = CrossSellHistory.builder()
                .opportunite(opportunite)
                .typeEvenement(type)
                .description(description)
                .auteur(resolveUser(principal.userId()))
                .build();
        crossSellHistoryRepository.save(evenement);
    }

    private void validerTransition(CrossSellStatut actuel, CrossSellStatut cible) {
        if (actuel == cible) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(actuel, Set.of()).contains(cible)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition " + actuel + " -> " + cible + " non autorisee");
        }
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private CrossSellOpportunity getScoped(Long id) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        CrossSellOpportunity opportunite = crossSellOpportunityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunite introuvable: " + id));
        if (FidelisationRoles.canViewAll(principal) || estAccessible(opportunite, principal)) {
            return opportunite;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunite introuvable: " + id);
    }

    private void checkAccess(Insured insured) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (FidelisationRoles.canViewAll(principal) || estAccessibleParClient(insured, principal)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + (insured == null ? null : insured.getId()));
    }

    private boolean estAccessible(CrossSellOpportunity opportunite, AuthenticatedUserPrincipal principal) {
        if (opportunite.getCommercial() != null && opportunite.getCommercial().getId().equals(principal.userId())) {
            return true;
        }
        return estAccessibleParClient(opportunite.getInsured(), principal);
    }

    private boolean estAccessibleParClient(Insured insured, AuthenticatedUserPrincipal principal) {
        if (insured == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .anyMatch(c -> c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId()));
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

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
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

    private CrossSellOpportunityResponse toResponse(CrossSellOpportunity o) {
        return new CrossSellOpportunityResponse(
                o.getId(),
                o.getCode(),
                o.getInsured() == null ? null : o.getInsured().getId(),
                insuredName(o.getInsured()),
                o.getContrat() == null ? null : o.getContrat().getId(),
                o.getContrat() == null ? null : o.getContrat().getPolicyNumber(),
                o.getProduitActuel() == null ? null : o.getProduitActuel().getId(),
                o.getProduitActuel() == null ? null : o.getProduitActuel().getNomProduit(),
                o.getProduitRecommande() == null ? null : o.getProduitRecommande().getId(),
                o.getProduitRecommande() == null ? null : o.getProduitRecommande().getNomProduit(),
                o.getScore(),
                o.getRegle() == null ? null : o.getRegle().getId(),
                o.getRegle() == null ? null : o.getRegle().getNom(),
                o.getCommercial() == null ? null : o.getCommercial().getId(),
                displayName(o.getCommercial()),
                o.getCampagne() == null ? null : o.getCampagne().getId(),
                o.getCampagne() == null ? null : o.getCampagne().getNom(),
                o.getPriorite(),
                o.getStatut(),
                o.getMotif(),
                o.getCommentaire(),
                o.getDateAffectation(),
                o.getDateTraitement(),
                o.getDateCloture(),
                o.getCreatedAt(),
                o.getLastUpdate()
        );
    }

    private CrossSellHistoryResponse toHistoryResponse(CrossSellHistory h) {
        return new CrossSellHistoryResponse(
                h.getId(),
                h.getOpportunite() == null ? null : h.getOpportunite().getId(),
                h.getTypeEvenement(),
                h.getDescription(),
                h.getAuteur() == null ? null : h.getAuteur().getId(),
                displayName(h.getAuteur()),
                h.getCreatedAt()
        );
    }
}
