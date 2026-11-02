package ci.assurzen.dto;

public record TemplatePreviewResponse(
        String objetResolu,
        String contenuResolu,
        int longueur,
        boolean depasseMaxCaracteres
) {
}
