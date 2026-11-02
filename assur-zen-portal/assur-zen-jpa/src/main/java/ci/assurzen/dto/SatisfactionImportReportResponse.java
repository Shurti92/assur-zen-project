package ci.assurzen.dto;

import ci.assurzen.entities.ImportErrorType;
import ci.assurzen.entities.SatisfactionImportStatut;

import java.util.Map;

public record SatisfactionImportReportResponse(
        Long importId,
        String code,
        SatisfactionImportStatut statut,
        int totalLignes,
        int lignesIgnorees,
        int lignesReussies,
        int lignesEnErreur,
        double tauxReussite,
        long promoteurs,
        long passifs,
        long detracteurs,
        Double scoreNpsMoyen,
        Map<ImportErrorType, Long> erreursParType
) {
}
