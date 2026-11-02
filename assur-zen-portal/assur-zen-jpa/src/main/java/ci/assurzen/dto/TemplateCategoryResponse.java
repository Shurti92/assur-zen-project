package ci.assurzen.dto;

public record TemplateCategoryResponse(
        Long id,
        String code,
        String libelle,
        String description,
        boolean active
) {
}
