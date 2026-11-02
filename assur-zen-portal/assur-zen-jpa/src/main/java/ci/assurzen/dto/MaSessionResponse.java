package ci.assurzen.dto;

import ci.assurzen.entities.DeviceType;

import java.time.Instant;

/** Un appareil connecte de l'utilisateur (GET /auth/me/sessions, 2026-10-03). */
public record MaSessionResponse(
        /** Identifiant stable de la session (a passer a DELETE /auth/me/sessions/{id}). */
        String id,
        DeviceType appareil,
        String navigateur,
        String os,
        /** Localisation approximative a partir de l'IP, null si inconnue. */
        String ville,
        String pays,
        String ip,
        Instant connecteeLe,
        /** Dernier renouvellement du jeton (environ toutes les heures tant que l'application est ouverte). */
        Instant derniereActivite,
        /** Session de l'appareil qui fait la requete. */
        boolean courante
) {
}
