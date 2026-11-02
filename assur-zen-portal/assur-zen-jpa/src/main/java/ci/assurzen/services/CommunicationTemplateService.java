package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommunicationTemplatePatchRequest;
import ci.assurzen.dto.CommunicationTemplateRequest;
import ci.assurzen.dto.CommunicationTemplateResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.TemplatePreviewRequest;
import ci.assurzen.dto.TemplatePreviewResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.CommunicationTemplate;
import ci.assurzen.entities.Langue;
import ci.assurzen.entities.TemplateCategory;
import ci.assurzen.entities.TemplateStatut;
import ci.assurzen.entities.TemplateVariable;
import ci.assurzen.mapper.CommunicationTemplateMapper;
import ci.assurzen.repositories.CommunicationTemplateRepository;
import ci.assurzen.repositories.TemplateVariableRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunicationTemplateService {

    private final CommunicationTemplateRepository communicationTemplateRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final TemplateCategoryService templateCategoryService;
    private final LangueService langueService;
    private final TemplateVariableResolver templateVariableResolver;
    private final AuthService authService;
    private final CommunicationTemplateMapper communicationTemplateMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<CommunicationTemplateResponse> search(CanalEnvoi canal, TemplateStatut statut, Long categorieId,
                                                                Boolean active, String q, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<CommunicationTemplateResponse> items = communicationTemplateRepository.findAll().stream()
                .filter(t -> canal == null || t.getCanal() == canal)
                .filter(t -> statut == null || t.getStatut() == statut)
                .filter(t -> categorieId == null || (t.getCategorie() != null && categorieId.equals(t.getCategorie().getId())))
                .filter(t -> active == null || t.isActive() == active)
                .filter(t -> normalizedQuery == null || matchesQuery(t, normalizedQuery))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(communicationTemplateMapper::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public CommunicationTemplateResponse get(Long id) {
        return communicationTemplateMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "TEMPLATES", entityName = "CommunicationTemplate")
    public CommunicationTemplateResponse create(CommunicationTemplateRequest request) {
        requireWriteAccess();
        communicationTemplateRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un template existe deja pour ce code: " + request.code());
        });
        validateObjetRequisSiCanal(request.canal(), request.objet());

        CommunicationTemplate template = CommunicationTemplate.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .canal(request.canal())
                .categorie(templateCategoryService.getEntity(request.categorieId()))
                .langue(langueService.getEntity(request.langueId()))
                .objet(request.objet())
                .contenu(request.contenu())
                .maxCaracteres(request.maxCaracteres())
                .statut(TemplateStatut.BROUILLON)
                .active(request.active() == null || request.active())
                .variables(resolveVariables(request.variableCodes()))
                .build();
        stampCreated(template);

        return communicationTemplateMapper.toResponse(communicationTemplateRepository.save(template));
    }

    @Transactional
    public CommunicationTemplateResponse update(Long id, CommunicationTemplateRequest request) {
        requireWriteAccess();
        CommunicationTemplate template = getEntity(id);
        CommunicationTemplateResponse avant = communicationTemplateMapper.toResponse(template);

        communicationTemplateRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un template existe deja pour ce code: " + request.code());
                });
        validateObjetRequisSiCanal(request.canal(), request.objet());

        template.setCode(request.code());
        template.setNom(request.nom());
        template.setDescription(request.description());
        template.setCanal(request.canal());
        template.setCategorie(templateCategoryService.getEntity(request.categorieId()));
        template.setLangue(langueService.getEntity(request.langueId()));
        template.setObjet(request.objet());
        template.setContenu(request.contenu());
        template.setMaxCaracteres(request.maxCaracteres());
        if (request.active() != null) {
            template.setActive(request.active());
        }
        template.setVariables(resolveVariables(request.variableCodes()));
        stampUpdated(template);

        CommunicationTemplateResponse apres = communicationTemplateMapper.toResponse(communicationTemplateRepository.save(template));
        functionalAuditRecorder.recordUpdate("TEMPLATES", "CommunicationTemplate", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    public CommunicationTemplateResponse patch(Long id, CommunicationTemplatePatchRequest request) {
        requireWriteAccess();
        CommunicationTemplate template = getEntity(id);
        CommunicationTemplateResponse avant = communicationTemplateMapper.toResponse(template);

        if (request.nom() != null) {
            template.setNom(request.nom());
        }
        if (request.description() != null) {
            template.setDescription(request.description());
        }
        if (request.categorieId() != null) {
            template.setCategorie(templateCategoryService.getEntity(request.categorieId()));
        }
        if (request.langueId() != null) {
            template.setLangue(langueService.getEntity(request.langueId()));
        }
        if (request.objet() != null) {
            template.setObjet(request.objet());
        }
        if (request.contenu() != null) {
            template.setContenu(request.contenu());
        }
        if (request.maxCaracteres() != null) {
            template.setMaxCaracteres(request.maxCaracteres());
        }
        if (request.variableCodes() != null) {
            template.setVariables(resolveVariables(request.variableCodes()));
        }
        if (request.active() != null) {
            template.setActive(request.active());
        }
        validateObjetRequisSiCanal(template.getCanal(), template.getObjet());
        stampUpdated(template);

        CommunicationTemplateResponse apres = communicationTemplateMapper.toResponse(communicationTemplateRepository.save(template));
        functionalAuditRecorder.recordUpdate("TEMPLATES", "CommunicationTemplate", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "TEMPLATES", entityName = "CommunicationTemplate")
    public void delete(Long id) {
        requireWriteAccess();
        CommunicationTemplate template = getEntity(id);
        template.setDeleted(true);
        stampUpdated(template);
        communicationTemplateRepository.save(template);
    }

    @Transactional
    @Auditable(action = ActionAudit.ACTIVATE, module = "TEMPLATES", entityName = "CommunicationTemplate")
    public CommunicationTemplateResponse enable(Long id) {
        requireWriteAccess();
        CommunicationTemplate template = getEntity(id);
        if (template.getMaxCaracteres() != null && template.getContenu().length() > template.getMaxCaracteres()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le contenu depasse maxCaracteres (" + template.getContenu().length() + " > " + template.getMaxCaracteres() + ") : impossible d'activer");
        }
        template.setStatut(TemplateStatut.ACTIF);
        stampUpdated(template);
        return communicationTemplateMapper.toResponse(communicationTemplateRepository.save(template));
    }

    @Transactional
    @Auditable(action = ActionAudit.DEACTIVATE, module = "TEMPLATES", entityName = "CommunicationTemplate")
    public CommunicationTemplateResponse disable(Long id) {
        requireWriteAccess();
        CommunicationTemplate template = getEntity(id);
        template.setStatut(TemplateStatut.ARCHIVE);
        stampUpdated(template);
        return communicationTemplateMapper.toResponse(communicationTemplateRepository.save(template));
    }

    @Transactional
    public CommunicationTemplateResponse duplicate(Long id) {
        requireWriteAccess();
        CommunicationTemplate original = getEntity(id);

        String baseCode = original.getCode() + "-COPIE";
        String candidateCode = baseCode;
        int suffix = 1;
        while (communicationTemplateRepository.findByCode(candidateCode).isPresent()) {
            candidateCode = baseCode + "-" + suffix++;
        }

        CommunicationTemplate copy = CommunicationTemplate.builder()
                .code(candidateCode)
                .nom(original.getNom() + " (copie)")
                .description(original.getDescription())
                .canal(original.getCanal())
                .categorie(original.getCategorie())
                .langue(original.getLangue())
                .objet(original.getObjet())
                .contenu(original.getContenu())
                .maxCaracteres(original.getMaxCaracteres())
                .statut(TemplateStatut.BROUILLON)
                .active(true)
                .variables(new HashSet<>(original.getVariables()))
                .build();
        stampCreated(copy);

        return communicationTemplateMapper.toResponse(communicationTemplateRepository.save(copy));
    }

    public TemplatePreviewResponse preview(Long id, TemplatePreviewRequest request) {
        CommunicationTemplate template = getEntity(id);
        var valeurs = request == null ? null : request.valeurs();

        String objetResolu = templateVariableResolver.resoudreAvecValeurs(template.getObjet(), valeurs);
        String contenuResolu = templateVariableResolver.resoudreAvecValeurs(template.getContenu(), valeurs);
        int longueur = contenuResolu == null ? 0 : contenuResolu.length();
        boolean depasse = template.getMaxCaracteres() != null && longueur > template.getMaxCaracteres();

        return new TemplatePreviewResponse(objetResolu, contenuResolu, longueur, depasse);
    }

    private Set<TemplateVariable> resolveVariables(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        List<TemplateVariable> found = templateVariableRepository.findByCodeIn(codes);
        if (found.size() != new HashSet<>(codes).size()) {
            List<String> foundCodes = found.stream().map(TemplateVariable::getCode).toList();
            List<String> missing = codes.stream().filter(c -> !foundCodes.contains(c)).toList();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variables inconnues: " + missing);
        }
        return new HashSet<>(found);
    }

    private void validateObjetRequisSiCanal(CanalEnvoi canal, String objet) {
        if ((canal == CanalEnvoi.EMAIL || canal == CanalEnvoi.PDF) && !StringUtils.hasText(objet)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objet est obligatoire pour un template EMAIL ou PDF");
        }
    }

    private boolean matchesQuery(CommunicationTemplate template, String normalizedQuery) {
        return contains(template.getCode(), normalizedQuery)
                || contains(template.getNom(), normalizedQuery)
                || contains(template.getDescription(), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    CommunicationTemplate getEntity(Long id) {
        return communicationTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private void stampCreated(CommunicationTemplate entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(CommunicationTemplate entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
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
}
