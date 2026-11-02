package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialObjectiveRequest;
import ci.assurzen.dto.CommercialObjectiveResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialObjective;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.repositories.CommercialObjectiveRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Objectif GLOBAL (annuel) d'un commercial - cf. javadoc CommercialObjective pour la distinction avec l'objectif mensuel. */
@Service
@RequiredArgsConstructor
public class CommercialObjectiveService {

    private final CommercialObjectiveRepository repository;
    private final ExerciceComptableRepository exerciceComptableRepository;
    private final CommercialService commercialService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "COMMERCIAL_OBJECTIVE", entityName = "CommercialObjective")
    public CommercialObjectiveResponse create(Long commercialId, CommercialObjectiveRequest request) {
        commercialService.requireAdmin();
        Commercial commercial = commercialService.getCommercial(commercialId);
        ExerciceComptable exercice = resolveExercice(request.exerciceComptableId());

        repository.findByCommercialIdAndExerciceComptableIdAndObjectiveType(commercialId, exercice.getId(), request.objectiveType())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un objectif " + request.objectiveType() + " existe deja pour ce commercial sur l'exercice " + exercice.getLibelle());
                });

        CommercialObjective objective = CommercialObjective.builder()
                .commercial(commercial)
                .exerciceComptable(exercice)
                .objectiveType(request.objectiveType())
                .targetAmount(request.targetAmount())
                .targetCount(request.targetCount())
                .build();
        stampCreated(objective);
        return toResponse(repository.save(objective));
    }

    @Transactional
    public CommercialObjectiveResponse update(Long commercialId, Long objectiveId, CommercialObjectiveRequest request) {
        commercialService.requireAdmin();
        CommercialObjective objective = getScoped(commercialId, objectiveId);
        CommercialObjectiveResponse avant = toResponse(objective);

        objective.setTargetAmount(request.targetAmount());
        objective.setTargetCount(request.targetCount());
        stampUpdated(objective);
        objective = repository.save(objective);

        CommercialObjectiveResponse apres = toResponse(objective);
        functionalAuditRecorder.recordUpdate("COMMERCIAL_OBJECTIVE", "CommercialObjective", String.valueOf(objectiveId), avant, apres, null);
        return apres;
    }

    public List<CommercialObjectiveResponse> list(Long commercialId) {
        commercialService.verifierAccesCommercialCible(commercialId);
        commercialService.getCommercial(commercialId);
        return repository.findByCommercialIdOrderByExerciceComptableIdDesc(commercialId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommercialObjective getScoped(Long commercialId, Long objectiveId) {
        CommercialObjective objective = repository.findById(objectiveId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable: " + objectiveId));
        if (!objective.getCommercial().getId().equals(commercialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable: " + objectiveId);
        }
        return objective;
    }

    private ExerciceComptable resolveExercice(Long exerciceComptableId) {
        return exerciceComptableRepository.findById(exerciceComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice comptable introuvable: " + exerciceComptableId));
    }

    private void stampCreated(CommercialObjective objective) {
        String username = authService.currentPrincipal().username();
        objective.setCreatedBy(username);
        objective.setUpdatedBy(username);
    }

    private void stampUpdated(CommercialObjective objective) {
        objective.setUpdatedBy(authService.currentPrincipal().username());
    }

    private CommercialObjectiveResponse toResponse(CommercialObjective objective) {
        ExerciceComptable exercice = objective.getExerciceComptable();
        return new CommercialObjectiveResponse(
                objective.getId(),
                objective.getCommercial().getId(),
                exercice.getId(),
                exercice.getLibelle(),
                objective.getObjectiveType(),
                objective.getTargetAmount(),
                objective.getTargetCount(),
                objective.getCreatedAt(),
                objective.getUpdatedAt()
        );
    }
}
