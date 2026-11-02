package ci.assurzen.dto;

public record SatisfactionParDimensionItem(
        Long id,
        String nom,
        Double satisfactionMoyenne,
        Integer nps,
        long reponses
) {
}
