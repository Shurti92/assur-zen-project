package ci.assurzen.dto;

public record LoginHistoryStatsResponse(
        long connexionsAujourdHui,
        long connexionsCetteSemaine,
        long connexionsCeMois
) {
}
