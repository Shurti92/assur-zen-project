package ci.assurzen.services;

import ci.assurzen.dto.CompagnieRequest;
import ci.assurzen.dto.CompagnieResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Compagnie;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CompagnieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompagnieService {

    private final CompagnieRepository repository;
    private final CodeGeneratorService codeGeneratorService;

    public PageResponse<CompagnieResponse> search(String q, Boolean active, Integer page, Integer size) {
        return paginate(filter(q, active), page, size);
    }

    private List<CompagnieResponse> filter(String q, Boolean active) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;

        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .filter(compagnie -> active == null || compagnie.isActive() == active)
                .filter(compagnie -> normalizedQuery == null
                        || contains(compagnie.getNom(), normalizedQuery)
                        || contains(compagnie.getCodeCompagnie(), normalizedQuery))
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

    public CompagnieResponse findById(Long id) {
        return toResponse(getCompagnie(id));
    }

    public CompagnieResponse create(CompagnieRequest request) {
        String codeCompagnie = StringUtils.hasText(request.codeCompagnie())
                ? request.codeCompagnie()
                : codeGeneratorService.generate(CodePrefix.COMPAGNIE);

        repository.findByCodeCompagnie(codeCompagnie)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une compagnie existe deja pour ce code: " + codeCompagnie);
                });

        Compagnie compagnie = Compagnie.builder()
                .codeCompagnie(codeCompagnie)
                .nom(request.nom())
                .active(request.active() == null || request.active())
                .type(request.type())
                .build();

        return toResponse(repository.save(compagnie));
    }

    public CompagnieResponse update(Long id, CompagnieRequest request) {
        Compagnie compagnie = getCompagnie(id);

        String codeCompagnie = StringUtils.hasText(request.codeCompagnie()) ? request.codeCompagnie() : compagnie.getCodeCompagnie();
        repository.findByCodeCompagnie(codeCompagnie)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une compagnie existe deja pour ce code: " + codeCompagnie);
                });

        compagnie.setCodeCompagnie(codeCompagnie);
        compagnie.setNom(request.nom());
        compagnie.setActive(request.active() == null || request.active());
        compagnie.setType(request.type());

        return toResponse(repository.save(compagnie));
    }

    public void delete(Long id) {
        repository.delete(getCompagnie(id));
    }

    private Compagnie getCompagnie(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compagnie introuvable: " + id));
    }

    private CompagnieResponse toResponse(Compagnie compagnie) {
        return new CompagnieResponse(
                compagnie.getId(),
                compagnie.getCodeCompagnie(),
                compagnie.getNom(),
                compagnie.isActive(),
                compagnie.getType(),
                compagnie.getCreation(),
                compagnie.getLastUpdate()
        );
    }
}
