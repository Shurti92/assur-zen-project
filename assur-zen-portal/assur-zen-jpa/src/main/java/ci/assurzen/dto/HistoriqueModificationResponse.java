package ci.assurzen.dto;

import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CanalAudit;
import ci.assurzen.entities.StatutAudit;

import java.time.Instant;

public record HistoriqueModificationResponse(
        Long id,
        String reference,
        String correlationId,
        String module,
        String entityName,
        String entityId,
        ActionAudit action,
        String description,
        String motif,
        String ancienneValeur,
        String nouvelleValeur,
        String champsModifies,
        Long userId,
        String username,
        String userRole,
        String ipAddress,
        CanalAudit canal,
        StatutAudit statut,
        String errorMessage,
        Instant createdAt
) {
}
