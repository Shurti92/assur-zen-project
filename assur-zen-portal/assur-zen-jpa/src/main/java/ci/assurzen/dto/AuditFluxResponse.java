package ci.assurzen.dto;

import ci.assurzen.enums.Direction;

import java.time.Instant;

public record AuditFluxResponse(
        Long id,
        String code,
        Instant creation,
        String operation,
        String flux,
        String exceptionMessage,
        Direction direction,
        String username,
        String numeroContrat,
        String route,
        Integer statusCode,
        String ipAddress,
        Long userId,
        Long durationMs
) {
}
