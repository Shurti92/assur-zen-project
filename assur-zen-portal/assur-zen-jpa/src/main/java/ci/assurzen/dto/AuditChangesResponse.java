package ci.assurzen.dto;

public record AuditChangesResponse(
        Long id,
        String entityName,
        String entityId,
        String ancienneValeur,
        String nouvelleValeur,
        String champsModifies
) {
}
