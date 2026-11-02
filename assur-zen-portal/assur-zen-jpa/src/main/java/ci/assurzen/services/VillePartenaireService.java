package ci.assurzen.services;

import ci.assurzen.dto.VillePartenaireRequest;
import ci.assurzen.dto.VillePartenaireResponse;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.repositories.VillePartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VillePartenaireService {

    private final VillePartenaireRepository repository;

    public List<VillePartenaireResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public VillePartenaireResponse findById(Long id) {
        return toResponse(getVillePartenaire(id));
    }

    public VillePartenaireResponse create(VillePartenaireRequest request) {
        VillePartenaire villePartenaire = VillePartenaire.builder()
                .nomVille(request.nomVille())
                .regions(request.regions())
                .active(request.active() == null || request.active())
                .build();

        return toResponse(repository.save(villePartenaire));
    }

    public VillePartenaireResponse update(Long id, VillePartenaireRequest request) {
        VillePartenaire villePartenaire = getVillePartenaire(id);

        villePartenaire.setNomVille(request.nomVille());
        villePartenaire.setRegions(request.regions());
        villePartenaire.setActive(request.active() == null || request.active());

        return toResponse(repository.save(villePartenaire));
    }

    public void delete(Long id) {
        repository.delete(getVillePartenaire(id));
    }

    private VillePartenaire getVillePartenaire(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VillePartenaire introuvable: " + id));
    }

    private VillePartenaireResponse toResponse(VillePartenaire villePartenaire) {
        return new VillePartenaireResponse(
                villePartenaire.getId(),
                villePartenaire.getNomVille(),
                villePartenaire.getRegions(),
                villePartenaire.isActive(),
                villePartenaire.getCreation(),
                villePartenaire.getLastUpdate()
        );
    }
}
