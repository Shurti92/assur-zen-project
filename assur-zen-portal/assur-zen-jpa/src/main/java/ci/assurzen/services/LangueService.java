package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.LangueRequest;
import ci.assurzen.dto.LangueResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Langue;
import ci.assurzen.mapper.LangueMapper;
import ci.assurzen.repositories.LangueRepository;
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
public class LangueService {

    private final LangueRepository langueRepository;
    private final LangueMapper langueMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<LangueResponse> list() {
        return langueRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(langueMapper::toResponse)
                .toList();
    }

    public LangueResponse get(Long id) {
        return langueMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "I18N", entityName = "Langue")
    public LangueResponse create(LangueRequest request) {
        requireWriteAccess();
        langueRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une langue existe deja pour ce code: " + request.code());
        });

        Langue langue = Langue.builder()
                .code(request.code())
                .libelle(request.libelle())
                .active(request.active() == null || request.active())
                .build();
        stampCreated(langue);

        return langueMapper.toResponse(langueRepository.save(langue));
    }

    @Transactional
    public LangueResponse update(Long id, LangueRequest request) {
        requireWriteAccess();
        Langue langue = getEntity(id);
        LangueResponse avant = langueMapper.toResponse(langue);

        langueRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une langue existe deja pour ce code: " + request.code());
                });

        langue.setCode(request.code());
        langue.setLibelle(request.libelle());
        if (request.active() != null) {
            langue.setActive(request.active());
        }
        stampUpdated(langue);

        LangueResponse apres = langueMapper.toResponse(langueRepository.save(langue));
        functionalAuditRecorder.recordUpdate("I18N", "Langue", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "I18N", entityName = "Langue")
    public void delete(Long id) {
        requireWriteAccess();
        Langue langue = getEntity(id);
        langue.setDeleted(true);
        stampUpdated(langue);
        langueRepository.save(langue);
    }

    Langue getEntity(Long id) {
        return langueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Langue introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private void stampCreated(Langue entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(Langue entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }
}
