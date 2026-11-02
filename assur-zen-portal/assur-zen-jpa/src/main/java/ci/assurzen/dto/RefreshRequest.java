package ci.assurzen.dto;

/**
 * refreshToken desormais optionnel (migration cookie httpOnly, 2026-08-23) : un client same-origin
 * (medicis-apps.com) peut omettre ce champ et laisser le navigateur envoyer automatiquement le cookie
 * "refresh_token" a la place. Un client cross-origin (ex. le frontend Vercel) continue de le fournir
 * ici exactement comme avant - LoginController accepte l'un OU l'autre, rejette si aucun des deux
 * n'est present.
 */
public record RefreshRequest(
        String refreshToken
) {
}
