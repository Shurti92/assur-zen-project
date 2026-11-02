package ci.assurzen.dto;

/** Version legere pour peupler des listes deroulantes (GET /product-types/active). */
public record ProductTypeSummaryResponse(
        Long id,
        String code,
        String nom,
        String icone,
        String couleur
) {
}
