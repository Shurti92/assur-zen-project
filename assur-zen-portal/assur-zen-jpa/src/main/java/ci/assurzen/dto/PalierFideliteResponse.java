package ci.assurzen.dto;

public record PalierFideliteResponse(
        Long id,
        String code,
        String libelle,
        Long seuilPoints,
        String avantages,
        String couleur,
        String icone,
        boolean estVip,
        boolean active
) {
}
