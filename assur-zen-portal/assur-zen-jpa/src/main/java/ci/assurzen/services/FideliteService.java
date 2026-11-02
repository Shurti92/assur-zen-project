package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CompteFideliteAvantageResponse;
import ci.assurzen.dto.CompteFideliteResponse;
import ci.assurzen.dto.FidelisationAjustementRequest;
import ci.assurzen.dto.FidelisationHistoriqueResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RecompenseEchangeRequest;
import ci.assurzen.dto.RecompenseEchangeResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CompteFidelite;
import ci.assurzen.entities.CompteFideliteAvantage;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.EchangeStatut;
import ci.assurzen.entities.FidelisationHistorique;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.MouvementFideliteType;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.entities.Recompense;
import ci.assurzen.entities.RecompenseEchange;
import ci.assurzen.entities.StatutVip;
import ci.assurzen.entities.User;
import ci.assurzen.mapper.CompteFideliteMapper;
import ci.assurzen.mapper.FidelisationHistoriqueMapper;
import ci.assurzen.mapper.RecompenseEchangeMapper;
import ci.assurzen.repositories.CompteFideliteAvantageRepository;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.FidelisationHistoriqueRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.PalierFideliteRepository;
import ci.assurzen.repositories.RecompenseEchangeRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Regle de gain de points validee avec l'utilisateur (voir doc de conception) : 1 point par tranche
 * de POINTS_PAR_TRANCHE_FCFA de prime payee, plus un bonus fixe au renouvellement d'un contrat
 * (Contract.contratPrecedent nouvellement renseigne).
 */
@Service
@RequiredArgsConstructor
public class FideliteService {

    // Valeurs de repli (Dynamic Configuration Engine, cles FIDELITE_POINTS_PAR_TRANCHE_FCFA /
    // FIDELITE_BONUS_RENOUVELLEMENT) - identiques a la regle validee avec l'utilisateur, inchangee
    // tant qu'un admin ne modifie pas les configurations correspondantes.
    private static final long DEFAULT_POINTS_PAR_TRANCHE_FCFA = 1000L;
    private static final long DEFAULT_BONUS_RENOUVELLEMENT = 500L;

    private final CompteFideliteRepository compteFideliteRepository;
    private final FidelisationHistoriqueRepository fidelisationHistoriqueRepository;
    private final RecompenseEchangeRepository recompenseEchangeRepository;
    private final PalierFideliteRepository palierFideliteRepository;
    private final CompteFideliteAvantageRepository compteFideliteAvantageRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RecompenseService recompenseService;
    private final CompteFideliteMapper compteFideliteMapper;
    private final FidelisationHistoriqueMapper fidelisationHistoriqueMapper;
    private final RecompenseEchangeMapper recompenseEchangeMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final DynamicConfigurationService dynamicConfigurationService;

    public CompteFideliteResponse getCompte(Long insuredId) {
        return compteFideliteMapper.toResponse(getScopedCompte(insuredId));
    }

    /** Liste/recherche des comptes fidelite, paginee, filtrable par statut VIP et/ou palier. */
    public PageResponse<CompteFideliteResponse> search(StatutVip statutVip, Long palierId, Integer page, Integer size) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean canViewAll = FidelisationRoles.canViewAll(principal);

        List<CompteFideliteResponse> items = compteFideliteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(c -> canViewAll || estAccessible(c, principal))
                .filter(c -> statutVip == null || c.getStatutVip() == statutVip)
                .filter(c -> palierId == null || (c.getPalier() != null && palierId.equals(c.getPalier().getId())))
                .map(compteFideliteMapper::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    /** Bascule manuelle du statut VIP (suspension/reactivation/sortie forcee), independamment du moteur de score. */
    @Transactional
    public CompteFideliteResponse changerStatutVip(Long insuredId, StatutVip nouveauStatut, String motif) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }

        Insured insured = resolveInsured(insuredId);
        CompteFidelite compte = getOrCreateCompte(insured);
        CompteFideliteResponse avant = compteFideliteMapper.toResponse(compte);
        StatutVip ancien = compte.getStatutVip();

        compte.setStatutVip(nouveauStatut);
        if (nouveauStatut == StatutVip.VIP_ACTIF && ancien != StatutVip.VIP_ACTIF) {
            compte.setDateEntreeVip(Instant.now());
            compte.setDateSortieVip(null);
        } else if (nouveauStatut != StatutVip.VIP_ACTIF && ancien == StatutVip.VIP_ACTIF) {
            compte.setDateSortieVip(Instant.now());
        }
        compte = compteFideliteRepository.save(compte);

        MouvementFideliteType type = nouveauStatut == StatutVip.VIP_ACTIF ? MouvementFideliteType.ENTREE_VIP : MouvementFideliteType.SORTIE_VIP;
        FidelisationHistorique mouvement = FidelisationHistorique.builder()
                .compteFidelite(compte)
                .typeMouvement(type)
                .points(0L)
                .soldeApres(compte.getSoldePoints())
                .motif("Changement manuel de statut VIP -> " + nouveauStatut + (motif != null ? " : " + motif : ""))
                .build();
        fidelisationHistoriqueRepository.save(mouvement);

        CompteFideliteResponse apres = compteFideliteMapper.toResponse(compte);
        functionalAuditRecorder.recordUpdate("FIDELITE", "CompteFidelite", String.valueOf(insuredId), avant, apres, motif);
        return apres;
    }

    /** Assigne (ou retire, si gestionnaireId est null) le gestionnaire dedie d'un compte VIP. */
    @Transactional
    public CompteFideliteResponse assignerGestionnaire(Long insuredId, Long gestionnaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }

        Insured insured = resolveInsured(insuredId);
        CompteFidelite compte = getOrCreateCompte(insured);
        CompteFideliteResponse avant = compteFideliteMapper.toResponse(compte);
        compte.setGestionnaireDedie(gestionnaireId != null ? resolveUser(gestionnaireId) : null);

        CompteFideliteResponse apres = compteFideliteMapper.toResponse(compteFideliteRepository.save(compte));
        functionalAuditRecorder.recordUpdate("FIDELITE", "CompteFidelite", String.valueOf(insuredId), avant, apres, null);
        return apres;
    }

    public List<CompteFideliteAvantageResponse> listAvantages(Long insuredId) {
        CompteFidelite compte = getScopedCompte(insuredId);
        return compteFideliteAvantageRepository.findByCompteFideliteIdOrderByDateAttributionDesc(compte.getId()).stream()
                .map(this::toAvantageResponse)
                .toList();
    }

    @Transactional
    public CompteFideliteResponse adherer(Long insuredId) {
        Insured insured = resolveInsured(insuredId);
        checkAccess(insured);
        return compteFideliteMapper.toResponse(getOrCreateCompte(insured));
    }

    public List<FidelisationHistoriqueResponse> listHistorique(Long insuredId) {
        CompteFidelite compte = getScopedCompte(insuredId);
        return fidelisationHistoriqueRepository.findByCompteFideliteIdOrderByCreatedAtDesc(compte.getId()).stream()
                .map(fidelisationHistoriqueMapper::toResponse)
                .toList();
    }

    @Transactional
    public FidelisationHistoriqueResponse ajusterManuellement(FidelisationAjustementRequest request) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }

        Insured insured = resolveInsured(request.insuredId());
        CompteFidelite compte = getOrCreateCompte(insured);
        CompteFideliteResponse avant = compteFideliteMapper.toResponse(compte);
        User auteur = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        FidelisationHistorique mouvement = appliquerMouvement(compte, MouvementFideliteType.AJUSTEMENT, request.points(),
                request.motif(), null, auteur);

        functionalAuditRecorder.recordUpdate("FIDELITE", "CompteFidelite", String.valueOf(request.insuredId()),
                avant, compteFideliteMapper.toResponse(compte), request.motif());
        return fidelisationHistoriqueMapper.toResponse(mouvement);
    }

    /** Appele par PaiementService apres enregistrement d'un paiement de prime. */
    @Transactional
    public void crediterPourPaiement(Contract contract, long montant) {
        if (contract.getInsured() == null || montant <= 0) {
            return;
        }
        long points = montant / dynamicConfigurationService.getLong("FIDELITE_POINTS_PAR_TRANCHE_FCFA", DEFAULT_POINTS_PAR_TRANCHE_FCFA);
        if (points <= 0) {
            return;
        }
        CompteFidelite compte = getOrCreateCompte(contract.getInsured());
        CompteFideliteResponse avant = compteFideliteMapper.toResponse(compte);
        appliquerMouvement(compte, MouvementFideliteType.GAIN, points,
                "Prime payee sur le contrat " + contract.getPolicyNumber(), contract, null);
        functionalAuditRecorder.recordUpdate("FIDELITE", "CompteFidelite", String.valueOf(contract.getInsured().getId()),
                avant, compteFideliteMapper.toResponse(compte), "Prime payee sur le contrat " + contract.getPolicyNumber());
    }

    /** Appele par ContractsService quand un contrat se voit rattacher un contratPrecedent (renouvellement). */
    @Transactional
    public void crediterBonusRenouvellement(Contract contract) {
        if (contract.getInsured() == null) {
            return;
        }
        CompteFidelite compte = getOrCreateCompte(contract.getInsured());
        CompteFideliteResponse avant = compteFideliteMapper.toResponse(compte);
        appliquerMouvement(compte, MouvementFideliteType.GAIN, dynamicConfigurationService.getLong("FIDELITE_BONUS_RENOUVELLEMENT", DEFAULT_BONUS_RENOUVELLEMENT),
                "Bonus de renouvellement du contrat " + contract.getPolicyNumber(), contract, null);
        functionalAuditRecorder.recordUpdate("FIDELITE", "CompteFidelite", String.valueOf(contract.getInsured().getId()),
                avant, compteFideliteMapper.toResponse(compte), "Bonus de renouvellement du contrat " + contract.getPolicyNumber());
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "RecompenseEchange")
    public RecompenseEchangeResponse echanger(RecompenseEchangeRequest request) {
        Insured insured = resolveInsured(request.insuredId());
        checkAccess(insured);
        CompteFidelite compte = getOrCreateCompte(insured);
        Recompense recompense = recompenseService.getEntity(request.recompenseId());

        if (!recompense.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette recompense n'est plus disponible");
        }
        if (recompense.getStock() != null && recompense.getStock() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock epuise pour cette recompense");
        }
        if (compte.getSoldePoints() < recompense.getCoutPoints()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solde de points insuffisant");
        }

        appliquerMouvement(compte, MouvementFideliteType.UTILISATION, -recompense.getCoutPoints(),
                "Echange contre la recompense " + recompense.getNom(), null, null);

        if (recompense.getStock() != null) {
            recompense.setStock(recompense.getStock() - 1);
        }

        RecompenseEchange echange = RecompenseEchange.builder()
                .compteFidelite(compte)
                .recompense(recompense)
                .pointsUtilises(recompense.getCoutPoints())
                .statut(EchangeStatut.EN_ATTENTE)
                .dateEchange(Instant.now())
                .build();

        return recompenseEchangeMapper.toResponse(recompenseEchangeRepository.save(echange));
    }

    @Transactional
    public RecompenseEchangeResponse traiterEchange(Long echangeId, EchangeStatut nouveauStatut) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }

        RecompenseEchange echange = recompenseEchangeRepository.findById(echangeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Echange introuvable: " + echangeId));
        RecompenseEchangeResponse avant = recompenseEchangeMapper.toResponse(echange);

        validerTransition(echange.getStatut(), nouveauStatut);

        if (nouveauStatut == EchangeStatut.ANNULEE) {
            appliquerMouvement(echange.getCompteFidelite(), MouvementFideliteType.AJUSTEMENT, echange.getPointsUtilises(),
                    "Remboursement suite annulation de l'echange #" + echange.getId(), null, null);
            Recompense recompense = echange.getRecompense();
            if (recompense.getStock() != null) {
                recompense.setStock(recompense.getStock() + 1);
            }
        }

        User traitePar = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        echange.setStatut(nouveauStatut);
        echange.setDateTraitement(Instant.now());
        echange.setTraitePar(traitePar);

        RecompenseEchangeResponse apres = recompenseEchangeMapper.toResponse(recompenseEchangeRepository.save(echange));
        functionalAuditRecorder.recordUpdate("FIDELITE", "RecompenseEchange", String.valueOf(echangeId), avant, apres, null);
        return apres;
    }

    private void validerTransition(EchangeStatut actuel, EchangeStatut cible) {
        boolean valide = switch (actuel) {
            case EN_ATTENTE -> cible == EchangeStatut.VALIDE || cible == EchangeStatut.ANNULEE;
            case VALIDE -> cible == EchangeStatut.LIVREE || cible == EchangeStatut.ANNULEE;
            case LIVREE, ANNULEE -> false;
        };
        if (!valide) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition " + actuel + " -> " + cible + " non autorisee");
        }
    }

    private FidelisationHistorique appliquerMouvement(CompteFidelite compte, MouvementFideliteType type, long points,
                                                        String motif, Contract contrat, User auteur) {
        long nouveauSolde = compte.getSoldePoints() + points;
        compte.setSoldePoints(nouveauSolde);
        if (type == MouvementFideliteType.GAIN) {
            compte.setPointsCumulesTotal(compte.getPointsCumulesTotal() + points);
            recalculerPalier(compte);
        }
        compteFideliteRepository.save(compte);

        FidelisationHistorique mouvement = FidelisationHistorique.builder()
                .compteFidelite(compte)
                .typeMouvement(type)
                .points(points)
                .soldeApres(nouveauSolde)
                .motif(motif)
                .contrat(contrat)
                .auteur(auteur)
                .build();

        return fidelisationHistoriqueRepository.save(mouvement);
    }

    /**
     * Resolution du palier par points cumules (mecanisme historique, anterieur au moteur de score
     * VIP configurable). Une fois qu'un compte a ete recalcule au moins une fois par
     * ScoreFideliteService (scoreFidelite non nul), ce mecanisme ne touche plus au palier : le score
     * pondere devient la seule source de verite pour ce compte, pour eviter qu'un simple encaissement
     * de prime n'ecrase silencieusement un palier VIP determine par des criteres multiples.
     */
    private void recalculerPalier(CompteFidelite compte) {
        if (compte.getScoreFidelite() != null) {
            return;
        }
        PalierFidelite palier = palierFideliteRepository
                .findFirstByActiveTrueAndSeuilPointsLessThanEqualOrderBySeuilPointsDesc(compte.getPointsCumulesTotal())
                .orElse(null);
        compte.setPalier(palier);
    }

    CompteFidelite getOrCreateCompte(Insured insured) {
        return compteFideliteRepository.findByInsuredId(insured.getId())
                .orElseGet(() -> compteFideliteRepository.save(CompteFidelite.builder()
                        .insured(insured)
                        .soldePoints(0L)
                        .pointsCumulesTotal(0L)
                        .dateAdhesion(LocalDate.now())
                        .active(true)
                        .build()));
    }

    private CompteFidelite getScopedCompte(Long insuredId) {
        Insured insured = resolveInsured(insuredId);
        checkAccess(insured);
        return compteFideliteRepository.findByInsuredId(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun compte fidelite pour cet assure: " + insuredId));
    }

    Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId));
    }

    void checkAccess(Insured insured) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (FidelisationRoles.canViewAll(principal)) {
            return;
        }
        boolean ownsAtLeastOneContract = contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .anyMatch(c -> c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId()));
        if (!ownsAtLeastOneContract) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insured.getId());
        }
    }

    private boolean estAccessible(CompteFidelite compte, AuthenticatedUserPrincipal principal) {
        if (compte.getGestionnaireDedie() != null && compte.getGestionnaireDedie().getId().equals(principal.userId())) {
            return true;
        }
        if (compte.getInsured() == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(compte.getInsured().getId()).stream()
                .anyMatch(c -> c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId()));
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + userId));
    }

    private CompteFideliteAvantageResponse toAvantageResponse(CompteFideliteAvantage a) {
        return new CompteFideliteAvantageResponse(
                a.getId(),
                a.getAvantage() == null ? null : a.getAvantage().getId(),
                a.getAvantage() == null ? null : a.getAvantage().getNom(),
                a.getAvantage() == null || a.getAvantage().getType() == null ? null : a.getAvantage().getType().name(),
                a.getDateAttribution(),
                a.getDateExpiration(),
                a.isActif()
        );
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
}
