package ci.assurzen.dto;

public record SatisfactionOverviewResponse(
        Double satisfactionMoyenne,
        Integer nps,
        long enquetesEnvoyees,
        long reponses,
        Double tauxReponse,
        long reclamationsOuvertes,
        long reclamationsResolues,
        Double delaiMoyenTraitementHeures
) {
}
