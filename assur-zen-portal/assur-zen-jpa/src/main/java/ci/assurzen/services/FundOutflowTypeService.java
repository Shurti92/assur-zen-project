package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.FundOutflowTypeRequest;
import ci.assurzen.dto.FundOutflowTypeResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.FundOutflowType;
import ci.assurzen.repositories.FundOutflowTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** CRUD des types de sortie de fonds (§4) - meme patron que GedDocumentTypeService/ProductTypeService. */
@Service
@RequiredArgsConstructor
public class FundOutflowTypeService {

    private static final String SYSTEM_CODE = "OTHER";

    private final FundOutflowTypeRepository repository;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<FundOutflowTypeResponse> search(String q, Boolean actif, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<FundOutflowTypeResponse> items = repository.findAll().stream()
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

    public FundOutflowTypeResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "SORTIES_FONDS", entityName = "FundOutflowType")
    public FundOutflowTypeResponse create(FundOutflowTypeRequest request) {
        checkCodeUnique(request.code(), null);

        FundOutflowType type = FundOutflowType.builder()
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
    public FundOutflowTypeResponse update(Long id, FundOutflowTypeRequest request) {
        FundOutflowType type = getEntity(id);
        FundOutflowTypeResponse avant = toResponse(type);
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

        FundOutflowTypeResponse apres = toResponse(repository.save(type));
        functionalAuditRecorder.recordUpdate("SORTIES_FONDS", "FundOutflowType", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "SORTIES_FONDS", entityName = "FundOutflowType")
    public void delete(Long id) {
        FundOutflowType type = getEntity(id);
        if (type.isSysteme()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce type de sortie systeme ne peut pas etre supprime: " + type.getCode());
        }
        type.setDeleted(true);
        repository.save(type);
    }

    FundOutflowType getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de sortie de fonds introuvable: " + id));
    }

    boolean isLoanType(FundOutflowType type) {
        return "LOAN".equalsIgnoreCase(type.getCode());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un type de sortie de fonds existe deja pour ce code: " + code);
                });
    }

    private boolean matchesQuery(FundOutflowType type, String normalizedQuery) {
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

    private FundOutflowTypeResponse toResponse(FundOutflowType t) {
        return new FundOutflowTypeResponse(
                t.getId(), t.getUuid(), t.getCode(), t.getNom(), t.getDescription(),
                t.getOrdre(), t.isActif(), t.isSysteme(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
