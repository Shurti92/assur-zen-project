package ci.assurzen.audit;

import ci.assurzen.entities.AuditFlux;
import ci.assurzen.repositories.AuditFluxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditFluxRepository auditFluxRepository;

    @Async("auditTaskExecutor")
    public void recordAsync(AuditRecord record) {
        try {
            AuditFlux entity = AuditFlux.builder()
                    .code(record.code())
                    .direction(record.direction())
                    .operation(record.operation())
                    .flux(record.flux() != null ? record.flux() : "")
                    .exceptionMessage(record.exceptionMessage())
                    .username(record.username())
                    .numeroContrat(record.numeroContrat())
                    .route(record.route())
                    .statusCode(record.statusCode())
                    .ipAddress(record.ipAddress())
                    .userId(record.userId())
                    .durationMs(record.durationMs())
                    .build();
            auditFluxRepository.save(entity);
        } catch (Exception e) {
            log.error("Audit write failed code={} direction={} route={}: {}",
                    record.code(), record.direction(), record.route(), e.getMessage(), e);
        }
    }
}
