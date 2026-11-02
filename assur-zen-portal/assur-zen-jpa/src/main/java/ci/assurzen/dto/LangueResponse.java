package ci.assurzen.dto;

public record LangueResponse(
        Long id,
        String code,
        String libelle,
        boolean active
) {
}
