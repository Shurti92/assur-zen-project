package ci.assurzen.dto;

/** GET /me/support-contact (2026-10-04) : chaque champ vaut null tant qu'il n'est pas renseigne en back-office. */
public record SupportContactResponse(
        String nom,
        String fonction,
        String telephone,
        String email,
        String whatsapp,
        String horaires,
        /** SUPPORT (contact general configure). GESTIONNAIRE reserve a un futur gestionnaire rattache. */
        String source
) {
}
