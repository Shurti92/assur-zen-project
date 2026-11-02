package ci.assurzen.dto;

public record NpsBreakdownResponse(
        long promoteurs,
        long passifs,
        long detracteurs,
        long totalReponses,
        Integer nps
) {
}
