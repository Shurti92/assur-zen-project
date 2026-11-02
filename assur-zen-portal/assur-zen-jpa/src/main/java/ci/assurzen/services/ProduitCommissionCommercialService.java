package ci.assurzen.services;

import ci.assurzen.dto.ProduitCommissionCommercialRequest;
import ci.assurzen.dto.ProduitCommissionCommercialResponse;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProduitCommissionCommercial;
import ci.assurzen.repositories.ProduitCommissionCommercialRepository;
import ci.assurzen.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import static ci.assurzen.utils.SafeAccess.safe;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** CRUD du taux/montant de commission commerciale par produit - mirroir de
 * ProduitCommissionTauxService, sans dimension TypePartenaire (un seul taux par produit). */
@Service
@RequiredArgsConstructor
public class ProduitCommissionCommercialService {

    private final ProduitCommissionCommercialRepository repository;
    private final ProduitRepository produitRepository;

    public List<ProduitCommissionCommercialResponse> findAll(Long produitId) {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation")).stream()
                .filter(taux -> produitId == null || (taux.getProduit() != null && produitId.equals(taux.getProduit().getId())))
                .map(this::toResponse)
                .toList();
    }

    public ProduitCommissionCommercialResponse findById(Long id) {
        return toResponse(getTaux(id));
    }

    public ProduitCommissionCommercialResponse create(ProduitCommissionCommercialRequest request) {
        LibTauxCommission.verifier(request.libTauxCommission());
        Produit produit = resolveProduit(request.produitId());

        repository.findByProduitId(request.produitId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Une commission commerciale existe deja pour ce produit");
                });

        ProduitCommissionCommercial taux = ProduitCommissionCommercial.builder()
                .produit(produit)
                .tauxCommission(request.tauxCommission())
                .libTauxCommission(request.libTauxCommission())
                .active(request.active() == null || request.active())
                .build();

        return toResponse(repository.save(taux));
    }

    public ProduitCommissionCommercialResponse update(Long id, ProduitCommissionCommercialRequest request) {
        ProduitCommissionCommercial taux = getTaux(id);
        LibTauxCommission.verifier(request.libTauxCommission());
        Produit produit = resolveProduit(request.produitId());

        repository.findByProduitId(request.produitId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Une commission commerciale existe deja pour ce produit");
                });

        taux.setProduit(produit);
        taux.setTauxCommission(request.tauxCommission());
        taux.setLibTauxCommission(request.libTauxCommission());
        taux.setActive(request.active() == null || request.active());

        return toResponse(repository.save(taux));
    }

    public void delete(Long id) {
        repository.delete(getTaux(id));
    }

    private ProduitCommissionCommercial getTaux(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission commerciale introuvable: " + id));
    }

    private Produit resolveProduit(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + produitId));
    }

    private ProduitCommissionCommercialResponse toResponse(ProduitCommissionCommercial taux) {
        return new ProduitCommissionCommercialResponse(
                taux.getId(),
                taux.getProduit() == null ? null : taux.getProduit().getId(),
                taux.getProduit() == null ? null : safe(taux.getProduit()::getNomProduit),
                taux.getTauxCommission(),
                taux.getLibTauxCommission(),
                taux.isActive(),
                taux.getCreation(),
                taux.getLastUpdate()
        );
    }
}
