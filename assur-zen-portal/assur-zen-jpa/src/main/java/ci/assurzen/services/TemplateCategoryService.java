package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.TemplateCategoryRequest;
import ci.assurzen.dto.TemplateCategoryResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.TemplateCategory;
import ci.assurzen.mapper.TemplateCategoryMapper;
import ci.assurzen.repositories.TemplateCategoryRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateCategoryService {

    private final TemplateCategoryRepository templateCategoryRepository;
    private final TemplateCategoryMapper templateCategoryMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<TemplateCategoryResponse> list() {
        return templateCategoryRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(templateCategoryMapper::toResponse)
                .toList();
    }

    public TemplateCategoryResponse get(Long id) {
        return templateCategoryMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "TEMPLATES", entityName = "TemplateCategory")
    public TemplateCategoryResponse create(TemplateCategoryRequest request) {
        requireWriteAccess();
        templateCategoryRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une categorie existe deja pour ce code: " + request.code());
        });

        TemplateCategory category = TemplateCategory.builder()
                .code(request.code())
                .libelle(request.libelle())
                .description(request.description())
                .active(request.active() == null || request.active())
                .build();
        stampCreated(category);

        return templateCategoryMapper.toResponse(templateCategoryRepository.save(category));
    }

    @Transactional
    public TemplateCategoryResponse update(Long id, TemplateCategoryRequest request) {
        requireWriteAccess();
        TemplateCategory category = getEntity(id);
        TemplateCategoryResponse avant = templateCategoryMapper.toResponse(category);

        templateCategoryRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une categorie existe deja pour ce code: " + request.code());
                });

        category.setCode(request.code());
        category.setLibelle(request.libelle());
        category.setDescription(request.description());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        stampUpdated(category);

        TemplateCategoryResponse apres = templateCategoryMapper.toResponse(templateCategoryRepository.save(category));
        functionalAuditRecorder.recordUpdate("TEMPLATES", "TemplateCategory", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "TEMPLATES", entityName = "TemplateCategory")
    public void delete(Long id) {
        requireWriteAccess();
        TemplateCategory category = getEntity(id);
        category.setDeleted(true);
        stampUpdated(category);
        templateCategoryRepository.save(category);
    }

    TemplateCategory getEntity(Long id) {
        return templateCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categorie de template introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private void stampCreated(TemplateCategory entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(TemplateCategory entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }
}
