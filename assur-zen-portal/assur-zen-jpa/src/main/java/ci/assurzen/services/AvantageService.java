package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AvantageRequest;
import ci.assurzen.dto.AvantageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Avantage;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.mapper.AvantageMapper;
import ci.assurzen.repositories.AvantageRepository;
import ci.assurzen.repositories.PalierFideliteRepository;
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
public class AvantageService {

    private final AvantageRepository avantageRepository;
    private final PalierFideliteRepository palierFideliteRepository;
    private final AvantageMapper avantageMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<AvantageResponse> list() {
        return avantageRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(avantageMapper::toResponse)
                .toList();
    }

    public AvantageResponse get(Long id) {
        return avantageMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "Avantage")
    public AvantageResponse create(AvantageRequest request) {
        requireCanManage();
        avantageRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un avantage existe deja pour ce code: " + request.code());
        });

        Avantage avantage = Avantage.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .type(request.type())
                .palier(request.palierId() != null ? resolvePalier(request.palierId()) : null)
                .valeur(request.valeur())
                .active(request.active() == null || request.active())
                .build();

        return avantageMapper.toResponse(avantageRepository.save(avantage));
    }

    @Transactional
    public AvantageResponse update(Long id, AvantageRequest request) {
        requireCanManage();
        Avantage avantage = getEntity(id);
        AvantageResponse avant = avantageMapper.toResponse(avantage);

        avantageRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un avantage existe deja pour ce code: " + request.code());
                });

        avantage.setCode(request.code());
        avantage.setNom(request.nom());
        avantage.setDescription(request.description());
        avantage.setType(request.type());
        avantage.setPalier(request.palierId() != null ? resolvePalier(request.palierId()) : null);
        avantage.setValeur(request.valeur());
        if (request.active() != null) {
            avantage.setActive(request.active());
        }

        AvantageResponse apres = avantageMapper.toResponse(avantageRepository.save(avantage));
        functionalAuditRecorder.recordUpdate("FIDELITE", "Avantage", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FIDELITE", entityName = "Avantage")
    public void delete(Long id) {
        requireCanManage();
        Avantage avantage = getEntity(id);
        avantage.setActive(false);
        avantageRepository.save(avantage);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private PalierFidelite resolvePalier(Long palierId) {
        return palierFideliteRepository.findById(palierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Palier de fidelite introuvable: " + palierId));
    }

    private Avantage getEntity(Long id) {
        return avantageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avantage introuvable: " + id));
    }
}
