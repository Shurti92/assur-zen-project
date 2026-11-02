package ci.assurzen.audit;

import ci.assurzen.enums.Direction;

public record AuditRecord(
        String code,
        Direction direction,
        String operation,
        String flux,
        String exceptionMessage,
        String username,
        String numeroContrat,
        String route,
        Integer statusCode,
        String ipAddress,
        Long userId,
        Long durationMs
) {
}
