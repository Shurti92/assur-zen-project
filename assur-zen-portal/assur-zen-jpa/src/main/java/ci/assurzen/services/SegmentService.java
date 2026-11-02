package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SegmentMembreResponse;
import ci.assurzen.dto.SegmentMembresRequest;
import ci.assurzen.dto.SegmentPatchRequest;
import ci.assurzen.dto.SegmentRequest;
import ci.assurzen.dto.SegmentResponse;
import ci.assurzen.dto.SegmentRuleRequest;
import ci.assurzen.dto.SegmentRuleResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Segment;
import ci.assurzen.entities.SegmentMembre;
import ci.assurzen.entities.SegmentRule;
import ci.assurzen.entities.SegmentType;
import ci.assurzen.mapper.SegmentMapper;
import ci.assurzen.mapper.SegmentMembreMapper;
import ci.assurzen.mapper.SegmentRuleMapper;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.SegmentMembreRepository;
import ci.assurzen.repositories.SegmentRepository;
import ci.assurzen.repositories.SegmentRuleRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentRuleRepository segmentRuleRepository;
    private final SegmentMembreRepository segmentMembreRepository;
    private final InsuredRepository insuredRepository;
    private final SegmentRuleEvaluator segmentRuleEvaluator;
    private final AuthService authService;
    private final SegmentMapper segmentMapper;
    private final SegmentRuleMapper segmentRuleMapper;
    private final SegmentMembreMapper segmentMembreMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<SegmentResponse> search(SegmentType type, Boolean active, String q, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<SegmentResponse> items = segmentRepository.findAll().stream()
                .filter(s -> type == null || s.getType() == type)
                .filter(s -> active == null || s.isActive() == active)
                .filter(s -> normalizedQuery == null || matchesQuery(s, normalizedQuery))
                .sorted(Comparator.comparing(Segment::getPriorite).reversed().thenComparing(Segment::getNom))
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public SegmentResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public List<InsuredResponse> resolve(Long id) {
        Segment segment = getEntity(id);
        return segmentRuleEvaluator.resolveMembres(segment).stream()
                .map(this::toInsuredResponse)
                .toList();
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "SEGMENTS", entityName = "Segment")
    public SegmentResponse create(SegmentRequest request) {
        requireWriteAccess();
        segmentRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un segment existe deja pour ce code: " + request.code());
        });

        Segment segment = Segment.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .type(request.type())
                .priorite(request.priorite() == null ? 0 : request.priorite())
                .couleur(request.couleur())
                .active(request.active() == null || request.active())
                .build();
        stampCreated(segment);

        return toResponse(segmentRepository.save(segment));
    }

    @Transactional
    public SegmentResponse update(Long id, SegmentRequest request) {
        requireWriteAccess();
        Segment segment = getEntity(id);
        SegmentResponse avant = toResponse(segment);

        segmentRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un segment existe deja pour ce code: " + request.code());
                });

        segment.setCode(request.code());
        segment.setNom(request.nom());
        segment.setDescription(request.description());
        segment.setType(request.type());
        segment.setPriorite(request.priorite() == null ? 0 : request.priorite());
        segment.setCouleur(request.couleur());
        if (request.active() != null) {
            segment.setActive(request.active());
        }
        stampUpdated(segment);

        SegmentResponse apres = toResponse(segmentRepository.save(segment));
        functionalAuditRecorder.recordUpdate("SEGMENTS", "Segment", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    public SegmentResponse patch(Long id, SegmentPatchRequest request) {
        requireWriteAccess();
        Segment segment = getEntity(id);
        SegmentResponse avant = toResponse(segment);

        if (request.nom() != null) {
            segment.setNom(request.nom());
        }
        if (request.description() != null) {
            segment.setDescription(request.description());
        }
        if (request.type() != null) {
            segment.setType(request.type());
        }
        if (request.priorite() != null) {
            segment.setPriorite(request.priorite());
        }
        if (request.couleur() != null) {
            segment.setCouleur(request.couleur());
        }
        if (request.active() != null) {
            segment.setActive(request.active());
        }
        stampUpdated(segment);

        SegmentResponse apres = toResponse(segmentRepository.save(segment));
        functionalAuditRecorder.recordUpdate("SEGMENTS", "Segment", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "SEGMENTS", entityName = "Segment")
    public void delete(Long id) {
        requireWriteAccess();
        Segment segment = getEntity(id);
        segment.setDeleted(true);
        stampUpdated(segment);
        segmentRepository.save(segment);
    }

    @Transactional
    @Auditable(action = ActionAudit.ACTIVATE, module = "SEGMENTS", entityName = "Segment")
    public SegmentResponse enable(Long id) {
        requireWriteAccess();
        Segment segment = getEntity(id);
        segment.setActive(true);
        stampUpdated(segment);
        return toResponse(segmentRepository.save(segment));
    }

    @Transactional
    @Auditable(action = ActionAudit.DEACTIVATE, module = "SEGMENTS", entityName = "Segment")
    public SegmentResponse disable(Long id) {
        requireWriteAccess();
        Segment segment = getEntity(id);
        segment.setActive(false);
        stampUpdated(segment);
        return toResponse(segmentRepository.save(segment));
    }

    public List<SegmentRuleResponse> listRules(Long segmentId) {
        getEntity(segmentId);
        return segmentRuleRepository.findBySegmentIdOrderByGroupeAscOrdreAsc(segmentId).stream()
                .map(segmentRuleMapper::toResponse)
                .toList();
    }

    @Transactional
    public SegmentRuleResponse addRule(Long segmentId, SegmentRuleRequest request) {
        requireWriteAccess();
        Segment segment = getEntity(segmentId);
        requireDynamic(segment);

        SegmentRule rule = SegmentRule.builder()
                .segment(segment)
                .champ(request.champ())
                .operateur(request.operateur())
                .valeur(request.valeur())
                .ordre(request.ordre() == null ? 0 : request.ordre())
                .groupe(request.groupe() == null ? 0 : request.groupe())
                .operateurLogique(request.operateurLogique() == null ? ci.assurzen.entities.LogicalOperator.AND : request.operateurLogique())
                .build();
        stampCreated(rule);

        return segmentRuleMapper.toResponse(segmentRuleRepository.save(rule));
    }

    @Transactional
    public SegmentRuleResponse updateRule(Long segmentId, Long ruleId, SegmentRuleRequest request) {
        requireWriteAccess();
        Segment segment = getEntity(segmentId);
        requireDynamic(segment);
        SegmentRule rule = segmentRuleRepository.findByIdAndSegmentId(ruleId, segmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regle introuvable: " + ruleId));

        rule.setChamp(request.champ());
        rule.setOperateur(request.operateur());
        rule.setValeur(request.valeur());
        rule.setOrdre(request.ordre() == null ? rule.getOrdre() : request.ordre());
        rule.setGroupe(request.groupe() == null ? rule.getGroupe() : request.groupe());
        if (request.operateurLogique() != null) {
            rule.setOperateurLogique(request.operateurLogique());
        }
        stampUpdated(rule);

        return segmentRuleMapper.toResponse(segmentRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long segmentId, Long ruleId) {
        requireWriteAccess();
        getEntity(segmentId);
        SegmentRule rule = segmentRuleRepository.findByIdAndSegmentId(ruleId, segmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regle introuvable: " + ruleId));
        rule.setDeleted(true);
        stampUpdated(rule);
        segmentRuleRepository.save(rule);
    }

    public List<SegmentMembreResponse> listMembres(Long segmentId) {
        getEntity(segmentId);
        return segmentMembreRepository.findBySegmentIdOrderByCreatedAtAsc(segmentId).stream()
                .map(segmentMembreMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<SegmentMembreResponse> replaceMembres(Long segmentId, SegmentMembresRequest request) {
        requireWriteAccess();
        Segment segment = getEntity(segmentId);
        if (segment.getType() != SegmentType.STATIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La gestion manuelle des membres n'est possible que pour un segment STATIC");
        }

        segmentMembreRepository.deleteBySegmentId(segmentId);
        List<Insured> insureds = insuredRepository.findAllById(request.insuredIds());
        List<SegmentMembre> membres = insureds.stream()
                .map(insured -> SegmentMembre.builder().segment(segment).insured(insured).build())
                .toList();

        return segmentMembreRepository.saveAll(membres).stream()
                .map(segmentMembreMapper::toResponse)
                .toList();
    }

    private void requireDynamic(Segment segment) {
        if (segment.getType() != SegmentType.DYNAMIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les regles ne s'appliquent qu'aux segments DYNAMIC");
        }
    }

    private boolean matchesQuery(Segment segment, String normalizedQuery) {
        return contains(segment.getCode(), normalizedQuery)
                || contains(segment.getNom(), normalizedQuery)
                || contains(segment.getDescription(), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private SegmentResponse toResponse(Segment segment) {
        long nbRegles = segmentRuleRepository.findBySegmentIdOrderByGroupeAscOrdreAsc(segment.getId()).size();
        long nbMembres = segment.getType() == SegmentType.STATIC
                ? segmentMembreRepository.findBySegmentIdOrderByCreatedAtAsc(segment.getId()).size()
                : segmentRuleEvaluator.resolveMembres(segment).size();
        return segmentMapper.toResponse(segment, nbRegles, nbMembres);
    }

    private InsuredResponse toInsuredResponse(Insured insured) {
        return new InsuredResponse(insured.getId(), insured.getNom(), insured.getPrenoms(), insured.getClientPhone(),
                insured.getClientCity(), insured.getClientEmail(), insured.getTypeClient(), insured.getClientPhone2());
    }

    private Segment getEntity(Long id) {
        return segmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Segment introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private void stampCreated(Segment entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(Segment entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }

    private void stampCreated(SegmentRule entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(SegmentRule entity) {
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
