package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.RecompenseRequest;
import ci.assurzen.dto.RecompenseResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Recompense;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.mapper.RecompenseMapper;
import ci.assurzen.repositories.RecompenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecompenseService {

    private final RecompenseRepository recompenseRepository;
    private final RecompenseMapper recompenseMapper;
    private final CodeGeneratorService codeGeneratorService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<RecompenseResponse> list() {
        return recompenseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(recompenseMapper::toResponse)
                .toList();
    }

    public RecompenseResponse get(Long id) {
        return recompenseMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "Recompense")
    public RecompenseResponse create(RecompenseRequest request) {
        String code = StringUtils.hasText(request.code()) ? request.code() : codeGeneratorService.generate(CodePrefix.RECOMPENSE_ECHANGE);
        recompenseRepository.findByCode(code).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une recompense existe deja pour ce code: " + code);
        });

        Recompense recompense = Recompense.builder()
                .code(code)
                .nom(request.nom())
                .description(request.description())
                .coutPoints(request.coutPoints())
                .stock(request.stock())
                .active(request.active() == null || request.active())
                .build();

        return recompenseMapper.toResponse(recompenseRepository.save(recompense));
    }

    @Transactional
    public RecompenseResponse update(Long id, RecompenseRequest request) {
        Recompense recompense = getEntity(id);
        RecompenseResponse avant = recompenseMapper.toResponse(recompense);

        recompense.setNom(request.nom());
        recompense.setDescription(request.description());
        recompense.setCoutPoints(request.coutPoints());
        recompense.setStock(request.stock());
        if (request.active() != null) {
            recompense.setActive(request.active());
        }

        RecompenseResponse apres = recompenseMapper.toResponse(recompenseRepository.save(recompense));
        functionalAuditRecorder.recordUpdate("FIDELITE", "Recompense", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FIDELITE", entityName = "Recompense")
    public void delete(Long id) {
        Recompense recompense = getEntity(id);
        recompense.setActive(false);
        recompenseRepository.save(recompense);
    }

    Recompense getEntity(Long id) {
        return recompenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recompense introuvable: " + id));
    }
}
