package ci.assurzen.services;

import ci.assurzen.dto.ProduitCommissionTauxRequest;
import ci.assurzen.dto.ProduitCommissionTauxResponse;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProduitCommissionTaux;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProduitCommissionTauxRepository;
import ci.assurzen.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import static ci.assurzen.utils.SafeAccess.safe;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProduitCommissionTauxService {

    private final ProduitCommissionTauxRepository repository;
    private final ProduitRepository produitRepository;
    private final PartenaireRepository partenaireRepository;

    public List<ProduitCommissionTauxResponse> findAll(Long produitId, PartenaireType typePartenaire, Long partenaireId) {
        PartenaireType effectiveType = typePartenaire;

        if (partenaireId != null) {
            Partenaire partenaire = partenaireRepository.findById(partenaireId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
            effectiveType = partenaire.getType();
        }

        PartenaireType finalEffectiveType = effectiveType;
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .filter(taux -> produitId == null || (taux.getProduit() != null && produitId.equals(taux.getProduit().getId())))
                .filter(taux -> finalEffectiveType == null || taux.getTypePartenaire() == finalEffectiveType)
                .map(this::toResponse)
                .toList();
    }

    public ProduitCommissionTauxResponse findById(Long id) {
        return toResponse(getTaux(id));
    }

    public ProduitCommissionTauxResponse create(ProduitCommissionTauxRequest request) {
        LibTauxCommission.verifier(request.libTauxCommission());
        Produit produit = resolveProduit(request.produitId());

        repository.findByProduitIdAndTypePartenaire(request.produitId(), request.typePartenaire())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un taux de commission existe deja pour ce produit et ce type de partenaire");
                });

        ProduitCommissionTaux taux = ProduitCommissionTaux.builder()
                .produit(produit)
                .typePartenaire(request.typePartenaire())
                .tauxCommission(request.tauxCommission())
                .libTauxCommission(request.libTauxCommission())
                .active(request.active() == null || request.active())
                .build();

        return toResponse(repository.save(taux));
    }

    public ProduitCommissionTauxResponse update(Long id, ProduitCommissionTauxRequest request) {
        ProduitCommissionTaux taux = getTaux(id);
        LibTauxCommission.verifier(request.libTauxCommission());
        Produit produit = resolveProduit(request.produitId());

        repository.findByProduitIdAndTypePartenaire(request.produitId(), request.typePartenaire())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un taux de commission existe deja pour ce produit et ce type de partenaire");
                });

        taux.setProduit(produit);
        taux.setTypePartenaire(request.typePartenaire());
        taux.setTauxCommission(request.tauxCommission());
        taux.setLibTauxCommission(request.libTauxCommission());
        taux.setActive(request.active() == null || request.active());

        return toResponse(repository.save(taux));
    }

    public void delete(Long id) {
        repository.delete(getTaux(id));
    }

    private ProduitCommissionTaux getTaux(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taux de commission introuvable: " + id));
    }

    private Produit resolveProduit(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + produitId));
    }

    private ProduitCommissionTauxResponse toResponse(ProduitCommissionTaux taux) {
        return new ProduitCommissionTauxResponse(
                taux.getId(),
                taux.getProduit() == null ? null : taux.getProduit().getId(),
                taux.getProduit() == null ? null : safe(taux.getProduit()::getNomProduit),
                taux.getTypePartenaire(),
                taux.getTauxCommission(),
                taux.getLibTauxCommission(),
                taux.isActive(),
                taux.getCreation(),
                taux.getLastUpdate()
        );
    }
}
