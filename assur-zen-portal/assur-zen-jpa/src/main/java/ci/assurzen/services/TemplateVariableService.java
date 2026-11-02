package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.TemplateVariableRequest;
import ci.assurzen.dto.TemplateVariableResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.TemplateVariable;
import ci.assurzen.mapper.TemplateVariableMapper;
import ci.assurzen.repositories.TemplateVariableRepository;
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
public class TemplateVariableService {

    private final TemplateVariableRepository templateVariableRepository;
    private final TemplateVariableMapper templateVariableMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<TemplateVariableResponse> list() {
        return templateVariableRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(templateVariableMapper::toResponse)
                .toList();
    }

    public TemplateVariableResponse get(Long id) {
        return templateVariableMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "TEMPLATES", entityName = "TemplateVariable")
    public TemplateVariableResponse create(TemplateVariableRequest request) {
        requireWriteAccess();
        templateVariableRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une variable existe deja pour ce code: " + request.code());
        });

        TemplateVariable variable = TemplateVariable.builder()
                .code(request.code())
                .libelle(request.libelle())
                .description(request.description())
                .typeDonnee(request.typeDonnee())
                .active(request.active() == null || request.active())
                .build();
        stampCreated(variable);

        return templateVariableMapper.toResponse(templateVariableRepository.save(variable));
    }

    @Transactional
    public TemplateVariableResponse update(Long id, TemplateVariableRequest request) {
        requireWriteAccess();
        TemplateVariable variable = getEntity(id);
        TemplateVariableResponse avant = templateVariableMapper.toResponse(variable);

        templateVariableRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une variable existe deja pour ce code: " + request.code());
                });

        variable.setCode(request.code());
        variable.setLibelle(request.libelle());
        variable.setDescription(request.description());
        variable.setTypeDonnee(request.typeDonnee());
        if (request.active() != null) {
            variable.setActive(request.active());
        }
        stampUpdated(variable);

        TemplateVariableResponse apres = templateVariableMapper.toResponse(templateVariableRepository.save(variable));
        functionalAuditRecorder.recordUpdate("TEMPLATES", "TemplateVariable", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "TEMPLATES", entityName = "TemplateVariable")
    public void delete(Long id) {
        requireWriteAccess();
        TemplateVariable variable = getEntity(id);
        variable.setDeleted(true);
        stampUpdated(variable);
        templateVariableRepository.save(variable);
    }

    TemplateVariable getEntity(Long id) {
        return templateVariableRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable de template introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private void stampCreated(TemplateVariable entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(TemplateVariable entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }
}
