package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentCategoryRequest;
import ci.assurzen.dto.GedDocumentCategoryResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.GedDocumentCategory;
import ci.assurzen.repositories.GedDocumentCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** CRUD des categories de document GED (§12) - meme patron que GedDocumentTypeService. La
 * categorie est optionnelle sur un GedDocument, donc pas de repli "systeme" obligatoire cote
 * service (contrairement au type, toujours requis). */
@Service
@RequiredArgsConstructor
public class GedDocumentCategoryService {

    private final GedDocumentCategoryRepository repository;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<GedDocumentCategoryResponse> search(String q, Boolean actif, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<GedDocumentCategoryResponse> items = repository.findAll().stream()
                .filter(c -> actif == null || c.isActif() == actif)
                .filter(c -> normalizedQuery == null || matchesQuery(c, normalizedQuery))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(a.getOrdre(), b.getOrdre());
                    return cmp != 0 ? cmp : a.getNom().compareToIgnoreCase(b.getNom());
                })
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public GedDocumentCategoryResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "GED", entityName = "GedDocumentCategory")
    public GedDocumentCategoryResponse create(GedDocumentCategoryRequest request) {
        checkCodeUnique(request.code(), null);

        GedDocumentCategory category = GedDocumentCategory.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .actif(request.actif() == null || request.actif())
                .systeme(false)
                .build();

        return toResponse(repository.save(category));
    }

    @Transactional
    public GedDocumentCategoryResponse update(Long id, GedDocumentCategoryRequest request) {
        GedDocumentCategory category = getEntity(id);
        GedDocumentCategoryResponse avant = toResponse(category);
        checkCodeUnique(request.code(), id);

        category.setCode(request.code());
        category.setNom(request.nom());
        category.setDescription(request.description());
        if (request.ordre() != null) {
            category.setOrdre(request.ordre());
        }
        if (request.actif() != null) {
            category.setActif(request.actif());
        }

        GedDocumentCategoryResponse apres = toResponse(repository.save(category));
        functionalAuditRecorder.recordUpdate("GED", "GedDocumentCategory", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "GED", entityName = "GedDocumentCategory")
    public void delete(Long id) {
        GedDocumentCategory category = getEntity(id);
        if (category.isSysteme()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette categorie systeme ne peut pas etre supprimee: " + category.getCode());
        }
        category.setDeleted(true);
        repository.save(category);
    }

    GedDocumentCategory getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categorie de document introuvable: " + id));
    }

    private void checkCodeUnique(String code, Long excludeId) {
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une categorie de document existe deja pour ce code: " + code);
                });
    }

    private boolean matchesQuery(GedDocumentCategory category, String normalizedQuery) {
        return contains(category.getCode(), normalizedQuery) || contains(category.getNom(), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
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

    private GedDocumentCategoryResponse toResponse(GedDocumentCategory c) {
        return new GedDocumentCategoryResponse(
                c.getId(), c.getUuid(), c.getCode(), c.getNom(), c.getDescription(),
                c.getOrdre(), c.isActif(), c.isSysteme(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
