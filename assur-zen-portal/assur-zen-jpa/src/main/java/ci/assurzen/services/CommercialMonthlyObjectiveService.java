package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialMonthlyObjectiveRequest;
import ci.assurzen.dto.CommercialMonthlyObjectiveResponse;
import ci.assurzen.dto.CommercialMonthlyObjectiveRevisionRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialMonthlyObjective;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.repositories.CommercialMonthlyObjectiveRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Objectif MENSUEL d'un commercial (§18-20). Une seule ligne active par (commercial, moisComptable,
 * type) - toute revision modifie cette meme ligne (motif obligatoire, §32/§33) plutot que d'en creer
 * une nouvelle ; l'historique est trace par le moteur d'audit fonctionnel existant
 * (FunctionalAuditRecorder.recordUpdate, expose via /audit-logs), pas de table dediee.
 */
@Service
@RequiredArgsConstructor
public class CommercialMonthlyObjectiveService {

    private final CommercialMonthlyObjectiveRepository repository;
    private final MoisComptableRepository moisComptableRepository;
    private final CommercialService commercialService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "COMMERCIAL_OBJECTIVE", entityName = "CommercialMonthlyObjective")
    public CommercialMonthlyObjectiveResponse create(Long commercialId, CommercialMonthlyObjectiveRequest request) {
        commercialService.requireAdmin();
        Commercial commercial = commercialService.getCommercial(commercialId);
        MoisComptable mois = resolveMois(request.moisComptableId());

        repository.findByCommercialIdAndMoisComptableIdAndObjectiveType(commercialId, mois.getId(), request.objectiveType())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un objectif " + request.objectiveType() + " existe deja pour ce commercial sur " + mois.getLibelle()
                                    + " - utiliser la revision plutot qu'une nouvelle creation");
                });

        CommercialMonthlyObjective objective = CommercialMonthlyObjective.builder()
                .commercial(commercial)
                .moisComptable(mois)
                .objectiveType(request.objectiveType())
                .targetAmount(request.targetAmount())
                .targetCount(request.targetCount())
                .build();
        stampCreated(objective);
        return toResponse(repository.save(objective));
    }

    @Transactional
    public CommercialMonthlyObjectiveResponse reviser(Long commercialId, Long objectiveId, CommercialMonthlyObjectiveRevisionRequest request) {
        commercialService.requireAdmin();
        CommercialMonthlyObjective objective = getScoped(commercialId, objectiveId);
        CommercialMonthlyObjectiveResponse avant = toResponse(objective);

        objective.setTargetAmount(request.targetAmount());
        objective.setTargetCount(request.targetCount());
        stampUpdated(objective);
        objective = repository.save(objective);

        CommercialMonthlyObjectiveResponse apres = toResponse(objective);
        functionalAuditRecorder.recordUpdate("COMMERCIAL_OBJECTIVE", "CommercialMonthlyObjective", String.valueOf(objectiveId), avant, apres, request.motif());
        return apres;
    }

    public List<CommercialMonthlyObjectiveResponse> list(Long commercialId) {
        commercialService.verifierAccesCommercialCible(commercialId);
        commercialService.getCommercial(commercialId);
        return repository.findByCommercialIdOrderByMoisComptable_NumeroDesc(commercialId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommercialMonthlyObjective getScoped(Long commercialId, Long objectiveId) {
        CommercialMonthlyObjective objective = repository.findById(objectiveId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif mensuel introuvable: " + objectiveId));
        if (!objective.getCommercial().getId().equals(commercialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif mensuel introuvable: " + objectiveId);
        }
        return objective;
    }

    private MoisComptable resolveMois(Long moisComptableId) {
        return moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));
    }

    private void stampCreated(CommercialMonthlyObjective objective) {
        String username = authService.currentPrincipal().username();
        objective.setCreatedBy(username);
        objective.setUpdatedBy(username);
    }

    private void stampUpdated(CommercialMonthlyObjective objective) {
        objective.setUpdatedBy(authService.currentPrincipal().username());
    }

    private CommercialMonthlyObjectiveResponse toResponse(CommercialMonthlyObjective objective) {
        MoisComptable mois = objective.getMoisComptable();
        return new CommercialMonthlyObjectiveResponse(
                objective.getId(),
                objective.getCommercial().getId(),
                mois.getId(),
                mois.getLibelle(),
                mois.getNumero(),
                objective.getObjectiveType(),
                objective.getTargetAmount(),
                objective.getTargetCount(),
                objective.getCreatedAt(),
                objective.getUpdatedAt()
        );
    }
}
