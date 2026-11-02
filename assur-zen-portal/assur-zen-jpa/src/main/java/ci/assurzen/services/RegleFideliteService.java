package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.RegleFideliteRequest;
import ci.assurzen.dto.RegleFideliteResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.RegleFidelite;
import ci.assurzen.mapper.RegleFideliteMapper;
import ci.assurzen.repositories.RegleFideliteRepository;
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
public class RegleFideliteService {

    private final RegleFideliteRepository regleFideliteRepository;
    private final RegleFideliteMapper regleFideliteMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<RegleFideliteResponse> list() {
        return regleFideliteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(regleFideliteMapper::toResponse)
                .toList();
    }

    public RegleFideliteResponse get(Long id) {
        return regleFideliteMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "RegleFidelite")
    public RegleFideliteResponse create(RegleFideliteRequest request) {
        requireCanManage();
        regleFideliteRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une regle existe deja pour ce code: " + request.code());
        });

        RegleFidelite regle = RegleFidelite.builder()
                .code(request.code())
                .nom(request.nom())
                .champ(request.champ())
                .operateur(request.operateur())
                .valeur(request.valeur())
                .poids(request.poids())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .actif(request.actif() == null || request.actif())
                .build();

        return regleFideliteMapper.toResponse(regleFideliteRepository.save(regle));
    }

    @Transactional
    public RegleFideliteResponse update(Long id, RegleFideliteRequest request) {
        requireCanManage();
        RegleFidelite regle = getEntity(id);
        RegleFideliteResponse avant = regleFideliteMapper.toResponse(regle);

        regleFideliteRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une regle existe deja pour ce code: " + request.code());
                });

        regle.setCode(request.code());
        regle.setNom(request.nom());
        regle.setChamp(request.champ());
        regle.setOperateur(request.operateur());
        regle.setValeur(request.valeur());
        regle.setPoids(request.poids());
        if (request.ordre() != null) {
            regle.setOrdre(request.ordre());
        }
        if (request.actif() != null) {
            regle.setActif(request.actif());
        }

        RegleFideliteResponse apres = regleFideliteMapper.toResponse(regleFideliteRepository.save(regle));
        functionalAuditRecorder.recordUpdate("FIDELITE", "RegleFidelite", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FIDELITE", entityName = "RegleFidelite")
    public void delete(Long id) {
        requireCanManage();
        RegleFidelite regle = getEntity(id);
        regle.setActif(false);
        regleFideliteRepository.save(regle);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private RegleFidelite getEntity(Long id) {
        return regleFideliteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regle de fidelite introuvable: " + id));
    }
}
