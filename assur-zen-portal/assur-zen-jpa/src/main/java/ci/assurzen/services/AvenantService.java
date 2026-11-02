package ci.assurzen.services;

import ci.assurzen.dto.AvenantRequest;
import ci.assurzen.dto.AvenantResponse;
import ci.assurzen.dto.ContractAvenantSummaryResponse;
import ci.assurzen.dto.HistoriqueContratResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Avenant;
import ci.assurzen.entities.CategorieAvenant;
import ci.assurzen.entities.Compagnie;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.HistoriqueModification;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.AvenantRepository;
import ci.assurzen.repositories.CompagnieRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.HistoriqueModificationRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Avenants sur un contrat deja signe (contrat "Espace Partenaire", decisions produit du 2026-08-23) :
 * seul point d'entree autorise pour modifier prime/commission/partenaire/compagnie une fois le contrat
 * signe (cf. ContractsService.update() qui rejette desormais ces champs a ce stade), plus une
 * classification complete (TypeAvenant) pour les autres natures de changement, meme quand elles ne
 * correspondent a aucun champ structure du schema actuel (garanties, bien assure, conducteur...) - dans
 * ce cas seuls motif/description en portent la trace.
 */
@Service
@RequiredArgsConstructor
public class AvenantService {

    private final AvenantRepository avenantRepository;
    private final ContractRepository contractRepository;
    private final PartenaireRepository partenaireRepository;
    private final CompagnieRepository compagnieRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final HistoriqueModificationRepository historiqueModificationRepository;
    private final AuthService authService;

    @Transactional
    public AvenantResponse creer(Long contractId, AvenantRequest request) {
        requireAdmin();

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));
        if (contract.getStatus() != ContractStatus.signed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les avenants ne s'appliquent qu'aux contrats signes (statut actuel: " + contract.getStatus() + ")");
        }

        User createdBy = userRepository.findById(authService.currentPrincipal().userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        Avenant.AvenantBuilder builder = Avenant.builder()
                .contract(contract)
                .numero((int) avenantRepository.countByContractId(contractId) + 1)
                .type(request.type())
                .categorie(request.type().getCategorie())
                .dateEffet(request.dateEffet())
                .motif(request.motif())
                .description(request.description())
                .createdBy(createdBy);

        applyChanges(contract, request, builder);

        Contract savedContract = contractRepository.save(contract);
        Avenant saved = avenantRepository.save(builder.build());

        return toResponse(saved, savedContract);
    }

    /**
     * Applique au contrat les seuls champs structures que ce projet modelise (prime, commission,
     * partenaire, compagnie) quand ils sont fournis et reellement differents de la valeur actuelle -
     * capture avant/apres sur l'avenant lui-meme. partnerShare (commission partenaire) ne change QUE
     * s'il est fourni explicitement (decision utilisateur du 2026-09-25) - plus aucun recalcul
     * automatique quand prime/commission/partenaire changent.
     */
    private void applyChanges(Contract contract, AvenantRequest request, Avenant.AvenantBuilder builder) {
        if (request.premium() != null && !Objects.equals(request.premium(), contract.getPremium())) {
            builder.ancienPremium(contract.getPremium()).nouveauPremium(request.premium());
            contract.setPremium(request.premium());
        }
        if (request.commissionRate() != null && (contract.getCommissionRate() == null
                || request.commissionRate().compareTo(contract.getCommissionRate()) != 0)) {
            builder.ancienneCommissionRate(contract.getCommissionRate()).nouvelleCommissionRate(request.commissionRate());
            contract.setCommissionRate(request.commissionRate());
        }
        boolean commissionModifiee = false;
        if (request.commissionAmount() != null && !Objects.equals(request.commissionAmount(), contract.getCommissionAmount())) {
            commissionModifiee = true;
            builder.ancienCommissionAmount(contract.getCommissionAmount()).nouveauCommissionAmount(request.commissionAmount());
            contract.setCommissionAmount(request.commissionAmount());
        }
        if (request.partenaireId() != null) {
            Long partenaireActuelId = contract.getPartenaire() == null ? null : contract.getPartenaire().getId();
            if (!request.partenaireId().equals(partenaireActuelId)) {
                Partenaire nouveauPartenaire = partenaireRepository.findById(request.partenaireId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + request.partenaireId()));
                builder.ancienPartenaire(contract.getPartenaire()).nouveauPartenaire(nouveauPartenaire);
                contract.setPartenaire(nouveauPartenaire);
            }
        }
        if (request.compagnieId() != null) {
            Long compagnieActuelleId = contract.getCompagnie() == null ? null : contract.getCompagnie().getId();
            if (!request.compagnieId().equals(compagnieActuelleId)) {
                Compagnie nouvelleCompagnie = compagnieRepository.findById(request.compagnieId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compagnie introuvable: " + request.compagnieId()));
                builder.ancienneCompagnie(contract.getCompagnie()).nouvelleCompagnie(nouvelleCompagnie);
                contract.setCompagnie(nouvelleCompagnie);
            }
        }

        if (request.partnerShare() != null) {
            long premium = contract.getPremium() == null ? 0L : contract.getPremium();
            if (request.partnerShare() < 0 || request.partnerShare() > premium) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "partnerShare doit etre compris entre 0 et la prime (" + premium + " FCFA), recu : " + request.partnerShare());
            }
            Long nouveauPartnerShare = contract.getPartenaire() == null ? 0L : request.partnerShare();
            if (!Objects.equals(nouveauPartnerShare, contract.getPartnerShare())) {
                builder.ancienPartnerShare(contract.getPartnerShare()).nouveauPartnerShare(nouveauPartnerShare);
                contract.setPartnerShare(nouveauPartnerShare);
            }
        }

        // Part partenaire <= commission (regle du 2026-09-25) : controlee quand l'une ou l'autre change.
        if (request.partnerShare() != null || commissionModifiee) {
            ContractsService.verifierPartSousCommission(contract.getPartnerShare(), contract.getCommissionAmount(),
                    request.partnerShare() != null ? ContractsService.OriginePart.SAISIE : ContractsService.OriginePart.ENREGISTREE);
        }
    }

    public List<AvenantResponse> listerPourContrat(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));
        verifierAccesContrat(contract);

        return avenantRepository.findByContractIdOrderByNumeroAsc(contractId).stream()
                .map(avenant -> toResponse(avenant, contract))
                .toList();
    }

    /**
     * Tous les contrats (tous partenaires confondus) ayant subi au moins un avenant, un resume agrege
     * par contrat (nombre + dernier avenant) - reserve aux administrateurs.
     */
    public PageResponse<ContractAvenantSummaryResponse> listerContratsAvecAvenant(Integer page, Integer size) {
        requireAdmin();
        return paginate(buildSummaries(avenantRepository.findAll()), page, size);
    }

    /**
     * Contrats d'un partenaire donne ayant subi au moins un avenant, meme portee que
     * historiquePartenaire (admin ou le partenaire lui-meme).
     */
    public PageResponse<ContractAvenantSummaryResponse> listerContratsAvecAvenantParPartenaire(Long partenaireId, Integer page, Integer size) {
        partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        verifierAccesPartenaire(partenaireId);
        return paginate(buildSummaries(avenantRepository.findByContract_Partenaire_IdOrderByCreatedAtDesc(partenaireId)), page, size);
    }

    private List<ContractAvenantSummaryResponse> buildSummaries(List<Avenant> avenants) {
        Map<Long, List<Avenant>> parContrat = avenants.stream()
                .collect(Collectors.groupingBy(avenant -> avenant.getContract().getId()));

        return parContrat.values().stream()
                .map(liste -> {
                    Avenant dernier = liste.stream().max(Comparator.comparing(Avenant::getNumero)).orElseThrow();
                    Contract contract = dernier.getContract();
                    return new ContractAvenantSummaryResponse(
                            contract.getId(), contract.getPolicyNumber(),
                            contract.getPartenaire() == null ? null : contract.getPartenaire().getId(),
                            contract.getPartenaire() == null ? null : nomPartenaire(contract.getPartenaire()),
                            liste.size(), dernier.getNumero(), dernier.getType(), dernier.getCreatedAt());
                })
                .sorted(Comparator.comparing(ContractAvenantSummaryResponse::dernierAvenantAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Historique complet (avenants + audit generique) de TOUS les contrats d'un partenaire, en une
     * seule timeline chronologique paginee. Reserve a l'admin ou au partenaire lui-meme (pas de
     * bypass commercial ici, contrairement a findByPartenaire : cette vue agrege des changements sur
     * des contrats potentiellement traites par plusieurs commerciaux differents).
     */
    public PageResponse<HistoriqueContratResponse> historiquePartenaire(Long partenaireId, Integer page, Integer size) {
        partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        verifierAccesPartenaire(partenaireId);

        List<Contract> contrats = contractRepository.findByPartenaireIdOrderByCreatedAtDesc(partenaireId);
        List<Long> contratIds = contrats.stream().map(Contract::getId).toList();

        List<HistoriqueContratResponse> entrees = new ArrayList<>();
        entrees.addAll(avenantRepository.findByContract_Partenaire_IdOrderByCreatedAtDesc(partenaireId).stream()
                .map(this::toHistoriqueEntry)
                .toList());
        entrees.addAll(historiqueModificationRepository
                .findByEntityNameAndEntityIdInOrderByCreatedAtDesc("Contract", contratIds.stream().map(String::valueOf).toList())
                .stream()
                .map(entry -> toHistoriqueEntry(entry, contrats))
                .toList());

        entrees.sort(Comparator.comparing(HistoriqueContratResponse::date, Comparator.nullsLast(Comparator.reverseOrder())));

        return paginate(entrees, page, size);
    }

    private HistoriqueContratResponse toHistoriqueEntry(Avenant avenant) {
        Contract contract = avenant.getContract();
        return new HistoriqueContratResponse(
                contract.getId(), contract.getPolicyNumber(), "AVENANT", avenant.getType().name(),
                avenant.getNumero(), avenant.getMotif(), avenant.getDescription(), null,
                avenant.getCreatedAt(), avenant.getCreatedBy() == null ? null : avenant.getCreatedBy().getId(),
                avenant.getCreatedBy() == null ? null : avenant.getCreatedBy().getUsername());
    }

    private HistoriqueContratResponse toHistoriqueEntry(HistoriqueModification entry, List<Contract> contrats) {
        String policyNumber = contrats.stream()
                .filter(c -> String.valueOf(c.getId()).equals(entry.getEntityId()))
                .findFirst()
                .map(Contract::getPolicyNumber)
                .orElse(null);
        return new HistoriqueContratResponse(
                entry.getEntityId() == null ? null : Long.valueOf(entry.getEntityId()), policyNumber, "AUDIT",
                entry.getAction() == null ? null : entry.getAction().name(), null,
                entry.getMotif(), entry.getDescription(), entry.getChampsModifies(),
                entry.getCreatedAt(), entry.getUserId(), entry.getUsername());
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

    private AvenantResponse toResponse(Avenant avenant, Contract contract) {
        return new AvenantResponse(
                avenant.getId(),
                contract.getId(),
                contract.getPolicyNumber(),
                avenant.getNumero(),
                avenant.getType(),
                avenant.getCategorie(),
                avenant.getDateEffet(),
                avenant.getMotif(),
                avenant.getDescription(),
                avenant.getAncienPremium(),
                avenant.getNouveauPremium(),
                avenant.getAncienneCommissionRate(),
                avenant.getNouvelleCommissionRate(),
                avenant.getAncienCommissionAmount(),
                avenant.getNouveauCommissionAmount(),
                avenant.getAncienPartnerShare(),
                avenant.getNouveauPartnerShare(),
                avenant.getAncienPartenaire() == null ? null : avenant.getAncienPartenaire().getId(),
                avenant.getAncienPartenaire() == null ? null : nomPartenaire(avenant.getAncienPartenaire()),
                avenant.getNouveauPartenaire() == null ? null : avenant.getNouveauPartenaire().getId(),
                avenant.getNouveauPartenaire() == null ? null : nomPartenaire(avenant.getNouveauPartenaire()),
                avenant.getAncienneCompagnie() == null ? null : avenant.getAncienneCompagnie().getId(),
                avenant.getAncienneCompagnie() == null ? null : avenant.getAncienneCompagnie().getNom(),
                avenant.getNouvelleCompagnie() == null ? null : avenant.getNouvelleCompagnie().getId(),
                avenant.getNouvelleCompagnie() == null ? null : avenant.getNouvelleCompagnie().getNom(),
                avenant.getCreatedBy() == null ? null : avenant.getCreatedBy().getId(),
                avenant.getCreatedBy() == null ? null : avenant.getCreatedBy().getUsername(),
                avenant.getCreatedAt()
        );
    }

    private String nomPartenaire(Partenaire partenaire) {
        return partenaire.getPrenoms() + " " + partenaire.getNom();
    }

    private void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private void verifierAccesContrat(Contract contract) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        boolean estCePartenaire = contract.getPartenaire() != null && estPartenaireConnecte(principal, contract.getPartenaire().getId());
        boolean estSonCommercial = contract.getSalesRep() != null && contract.getSalesRep().getId().equals(principal.userId());
        if (!estCePartenaire && !estSonCommercial) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire, son commercial, ou un administrateur");
        }
    }

    private void verifierAccesPartenaire(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        if (!estPartenaireConnecte(principal, partenaireId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    private boolean estPartenaireConnecte(AuthenticatedUserPrincipal principal, Long partenaireId) {
        return profileRepository.findByUserId(principal.userId())
                .map(Profile::getId)
                .map(profileId -> partenaireRepository.findByProfile_Id(profileId))
                .map(partenaires -> partenaires.stream().anyMatch(p -> p.getId().equals(partenaireId)))
                .orElse(false);
    }
}
