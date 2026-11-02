package ci.assurzen.dto;

import java.util.List;

public record HistoriqueModificationStatistiquesResponse(
        long total,
        long echecs,
        long exports,
        long connexions,
        List<AuditCompteItem> parAction,
        List<AuditCompteItem> parModule,
        List<AuditJourItem> parJour,
        List<AuditCompteItem> parUtilisateur
) {
}
