package ci.assurzen.dto;

import ci.assurzen.entities.NotificationCategorie;
import jakarta.validation.constraints.NotNull;

/**
 * Une ligne de Parametres > Notifications (GET/PUT /notifications/preferences, 2026-10-03). En entree
 * (PUT), seuls categorie, push et sms sont lus ; les champs *Modifiable sont ignores.
 */
public record NotificationPreferenceDto(
        @NotNull NotificationCategorie categorie,
        boolean push,
        boolean sms,
        /** false : le push ne peut pas etre coupe pour cette categorie (aucune a ce jour). */
        Boolean pushModifiable,
        /** false : le SMS reste toujours envoye (argent verse, bonus). */
        Boolean smsModifiable,
        /** true si au moins un canal est reglable. */
        Boolean modifiable
) {
}
