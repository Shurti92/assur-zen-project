package ci.assurzen.dto;

public record SatisfactionMensuelleResponse(
        String mois,
        Double satisfactionMoyenne,
        Integer nps,
        long reponses
) {
}
