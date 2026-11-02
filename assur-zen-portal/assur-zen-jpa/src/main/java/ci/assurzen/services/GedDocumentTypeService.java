package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentTypeRequest;
import ci.assurzen.dto.GedDocumentTypeResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.GedDocumentType;
import ci.assurzen.repositories.GedDocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** CRUD des types de document GED (§11) - meme patron que ProductTypeService, adapte a une entite
 * de reference plus simple (pas d'icone/couleur, pas de compteur d'usages). */
@Service
@RequiredArgsConstructor
public class GedDocumentTypeService {

    private static final String SYSTEM_CODE = "AUTRE";

    private final GedDocumentTypeRepository repository;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<GedDocumentTypeResponse> search(String q, Boolean actif, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<GedDocumentTypeResponse> items = repository.findAll().stream()
                .filter(t -> actif == null || t.isActif() == actif)
                .filter(t -> normalizedQuery == null || matchesQuery(t, normalizedQuery))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(a.getOrdre(), b.getOrdre());
                    return cmp != 0 ? cmp : a.getNom().compareToIgnoreCase(b.getNom());
                })
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public GedDocumentTypeResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "GED", entityName = "GedDocumentType")
    public GedDocumentTypeResponse create(GedDocumentTypeRequest request) {
        checkCodeUnique(request.code(), null);

        GedDocumentType type = GedDocumentType.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .actif(request.actif() == null || request.actif())
                .systeme(false)
                .build();

        return toResponse(repository.save(type));
    }

    @Transactional
    public GedDocumentTypeResponse update(Long id, GedDocumentTypeRequest request) {
        GedDocumentType type = getEntity(id);
        GedDocumentTypeResponse avant = toResponse(type);
        checkCodeUnique(request.code(), id);

        type.setCode(request.code());
        type.setNom(request.nom());
        type.setDescription(request.description());
        if (request.ordre() != null) {
            type.setOrdre(request.ordre());
        }
        if (request.actif() != null) {
            type.setActif(request.actif());
        }

        GedDocumentTypeResponse apres = toResponse(repository.save(type));
        functionalAuditRecorder.recordUpdate("GED", "GedDocumentType", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "GED", entityName = "GedDocumentType")
    public void delete(Long id) {
        GedDocumentType type = getEntity(id);
        if (type.isSysteme()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce type de document systeme ne peut pas etre supprime: " + type.getCode());
        }
        type.setDeleted(true);
        repository.save(type);
    }

    GedDocumentType getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de document introuvable: " + id));
    }

    /** Type de repli utilise par GedDocumentService quand un besoin de type systeme se presente. */
    GedDocumentType resolveOuCreerTypeParDefaut() {
        return repository.findByCodeIgnoreCase(SYSTEM_CODE)
                .orElseGet(() -> repository.save(GedDocumentType.builder()
                        .code(SYSTEM_CODE)
                        .nom("Autre")
                        .ordre(999)
                        .actif(true)
                        .systeme(true)
                        .build()));
    }

    private void checkCodeUnique(String code, Long excludeId) {
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un type de document existe deja pour ce code: " + code);
                });
    }

    private boolean matchesQuery(GedDocumentType type, String normalizedQuery) {
        return contains(type.getCode(), normalizedQuery) || contains(type.getNom(), normalizedQuery);
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

    private GedDocumentTypeResponse toResponse(GedDocumentType t) {
        return new GedDocumentTypeResponse(
                t.getId(), t.getUuid(), t.getCode(), t.getNom(), t.getDescription(),
                t.getOrdre(), t.isActif(), t.isSysteme(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
