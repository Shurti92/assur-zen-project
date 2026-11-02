package ci.assurzen.dto;

import ci.assurzen.entities.DeviceType;

import java.time.Instant;

/** Une ligne de l'historique de connexion de l'utilisateur connecte (GET /auth/me/login-history, 2026-10-03). */
public record MaConnexionResponse(
        Long id,
        Instant date,
        /** SUCCES | ECHEC (mot de passe incorrect). */
        String resultat,
        /** MOT_DE_PASSE pour un echec, null sinon. */
        String motifEchec,
        DeviceType deviceType,
        String navigateur,
        String os,
        /** Localisation approximative a partir de l'IP, null si inconnue (IP locale...). */
        String ville,
        String pays,
        String ip,
        /** Deja signalee "Ce n'etait pas moi". */
        boolean signale
) {
}
