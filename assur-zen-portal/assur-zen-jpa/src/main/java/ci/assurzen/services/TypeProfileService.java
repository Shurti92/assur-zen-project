package ci.assurzen.services;

import ci.assurzen.dto.TypeProfileRequest;
import ci.assurzen.dto.TypeProfileResponse;
import ci.assurzen.entities.TypeProfile;
import ci.assurzen.repositories.TypeProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TypeProfileService {

    private final TypeProfileRepository repository;

    public List<TypeProfileResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TypeProfileResponse findById(Long id) {
        return toResponse(getTypeProfile(id));
    }

    public TypeProfileResponse create(TypeProfileRequest request) {
        repository.findByLibelleIgnoreCase(request.libelle())
                .ifPresent(typeProfile -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "TypeProfile déjà existant: " + request.libelle());
                });

        TypeProfile typeProfile = TypeProfile.builder()
                .libelle(request.libelle())
                .active(request.active() == null || request.active())
                .declencheCreationCompte(Boolean.TRUE.equals(request.declencheCreationCompte()))
                .creation(Instant.now())
                .lastUpdate(Instant.now())
                .build();

        return toResponse(repository.save(typeProfile));
    }

    public TypeProfileResponse update(Long id, TypeProfileRequest request) {
        TypeProfile typeProfile = getTypeProfile(id);

        repository.findByLibelleIgnoreCase(request.libelle())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "TypeProfile déjà existant: " + request.libelle());
                });

        typeProfile.setLibelle(request.libelle());
        typeProfile.setLastUpdate(Instant.now());
        typeProfile.setActive(request.active() == null || request.active());
        typeProfile.setDeclencheCreationCompte(Boolean.TRUE.equals(request.declencheCreationCompte()));

        return toResponse(repository.save(typeProfile));
    }

    public void delete(Long id) {
        repository.delete(getTypeProfile(id));
    }

    private TypeProfile getTypeProfile(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TypeProfile introuvable: " + id));
    }

    private TypeProfileResponse toResponse(TypeProfile typeProfile) {
        return new TypeProfileResponse(
                typeProfile.getId(),
                typeProfile.getLibelle(),
                typeProfile.isActive(),
                typeProfile.isDeclencheCreationCompte(),
                typeProfile.getCreation(),
                typeProfile.getLastUpdate()
        );
    }
}
