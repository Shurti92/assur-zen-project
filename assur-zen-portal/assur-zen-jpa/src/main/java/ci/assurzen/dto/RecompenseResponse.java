package ci.assurzen.dto;

public record RecompenseResponse(
        Long id,
        String code,
        String nom,
        String description,
        Long coutPoints,
        Integer stock,
        boolean active
) {
}
