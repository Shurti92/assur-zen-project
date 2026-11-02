package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.VehiculeRequest;
import ci.assurzen.dto.VehiculeResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VehiculeService {

    private final VehiculeRepository vehiculeRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;

    public PageResponse<VehiculeResponse> search(String immatriculation, Long proprietaireId, VehiculeType type,
                                                  VehiculeStatut statut, Integer page, Integer size) {
        return paginate(filter(immatriculation, proprietaireId, type, statut), page, size);
    }

    public VehiculeResponse getVehicule(Long id) {
        return toResponse(resolveVehicule(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FLOTTE", entityName = "Vehicule")
    public VehiculeResponse create(VehiculeRequest request) {
        String immatriculation = normalize(request.immatriculation());
        if (immatriculation != null) {
            vehiculeRepository.findByImmatriculation(immatriculation).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un vehicule existe deja pour cette immatriculation: " + immatriculation);
            });
        }
        if (StringUtils.hasText(request.numeroChassis())) {
            vehiculeRepository.findByNumeroChassis(request.numeroChassis()).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un vehicule existe deja pour ce numero de chassis: " + request.numeroChassis());
            });
        }

        Vehicule vehicule = Vehicule.builder()
                .immatriculation(immatriculation)
                .type(request.type())
                .marque(request.marque())
                .modele(request.modele())
                .numeroChassis(request.numeroChassis())
                .dateMiseEnCirculation(request.dateMiseEnCirculation())
                .puissanceFiscale(request.puissanceFiscale())
                .cylindree(request.cylindree())
                .couleur(request.couleur())
                .proprietaire(resolveInsured(request.proprietaireId()))
                .statut(request.statut() != null ? request.statut() : VehiculeStatut.ACTIF)
                .build();

        return toResponse(vehiculeRepository.save(vehicule));
    }

    @Transactional
    public VehiculeResponse update(Long id, VehiculeRequest request) {
        Vehicule vehicule = resolveVehicule(id);

        String immatriculation = normalize(request.immatriculation());
        if (immatriculation != null) {
            vehiculeRepository.findByImmatriculation(immatriculation)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Un vehicule existe deja pour cette immatriculation: " + immatriculation);
                    });
        }

        vehicule.setImmatriculation(immatriculation);
        vehicule.setType(request.type());
        vehicule.setMarque(request.marque());
        vehicule.setModele(request.modele());
        vehicule.setNumeroChassis(request.numeroChassis());
        vehicule.setDateMiseEnCirculation(request.dateMiseEnCirculation());
        vehicule.setPuissanceFiscale(request.puissanceFiscale());
        vehicule.setCylindree(request.cylindree());
        vehicule.setCouleur(request.couleur());
        vehicule.setProprietaire(resolveInsured(request.proprietaireId()));
        if (request.statut() != null) {
            vehicule.setStatut(request.statut());
        }

        return toResponse(vehiculeRepository.save(vehicule));
    }

    /** Suppression logique. Un vehicule rattache a une flotte doit d'abord en etre retire (meme
     * garde-fou explicite que le contrat propose par le frontend) - une fois retire, currentFleet
     * repasse a null par FleetService.removeVehicle, donc ce controle seul suffit sans avoir besoin
     * de re-verifier l'historique FleetVehicle (conserve intact par la suppression logique). */
    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FLOTTE", entityName = "Vehicule")
    public void delete(Long id) {
        Vehicule vehicule = resolveVehicule(id);
        if (vehicule.getCurrentFleet() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce vehicule est actuellement rattache a une flotte (id " + vehicule.getCurrentFleet().getId() + "), retirez-le d'abord");
        }
        if (contractRepository.existsByVehiculeId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce vehicule est associe a au moins un contrat, suppression impossible");
        }
        vehicule.setDeleted(true);
        vehiculeRepository.save(vehicule);
    }

    Vehicule resolveVehicule(Long id) {
        return vehiculeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule introuvable: " + id));
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable: " + insuredId));
    }

    /** Nullable en sortie (2026-09-13) : une immatriculation absente/vide reste absente, jamais une
     * chaine vide - la cle unique en base ne tolere pas deux chaines vides identiques (seul NULL est
     * deduplicable a volonte), meme lecon que numeroChassis dans VehiculeImportParser. */
    private String normalize(String immatriculation) {
        return StringUtils.hasText(immatriculation) ? immatriculation.trim().toUpperCase(Locale.ROOT) : null;
    }

    private List<VehiculeResponse> filter(String immatriculation, Long proprietaireId, VehiculeType type, VehiculeStatut statut) {
        String normalizedQuery = StringUtils.hasText(immatriculation) ? immatriculation.trim().toUpperCase(Locale.ROOT) : null;
        return vehiculeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(vehicule -> normalizedQuery == null || (vehicule.getImmatriculation() != null && vehicule.getImmatriculation().contains(normalizedQuery)))
                .filter(vehicule -> proprietaireId == null || (vehicule.getProprietaire() != null && proprietaireId.equals(vehicule.getProprietaire().getId())))
                .filter(vehicule -> type == null || vehicule.getType() == type)
                .filter(vehicule -> statut == null || vehicule.getStatut() == statut)
                .map(this::toResponse)
                .toList();
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

    VehiculeResponse toResponse(Vehicule vehicule) {
        return new VehiculeResponse(
                vehicule.getId(),
                vehicule.getImmatriculation(),
                vehicule.getType(),
                vehicule.getMarque(),
                vehicule.getModele(),
                vehicule.getNumeroChassis(),
                vehicule.getDateMiseEnCirculation(),
                vehicule.getPuissanceFiscale(),
                vehicule.getCylindree(),
                vehicule.getCouleur(),
                vehicule.getProprietaire() == null ? null : vehicule.getProprietaire().getId(),
                vehicule.getProprietaire() == null ? null : vehicule.getProprietaire().getPrenoms() + " " + vehicule.getProprietaire().getNom(),
                vehicule.getStatut(),
                vehicule.getCurrentFleet() == null ? null : vehicule.getCurrentFleet().getId(),
                vehicule.getCurrentFleet() == null ? null : vehicule.getCurrentFleet().getFleetNumber(),
                vehicule.getCreatedAt(),
                vehicule.getUpdatedAt()
        );
    }
}
