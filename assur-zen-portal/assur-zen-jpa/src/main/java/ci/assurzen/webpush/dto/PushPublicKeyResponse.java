package ci.assurzen.webpush.dto;

/** GET /notifications/push/public-key : cle publique VAPID (applicationServerKey du frontend, base64url).
 * enabled = false (publicKey null) si le push n'est pas configure/active dans cet environnement : le
 * frontend ne propose alors pas l'abonnement. */
public record PushPublicKeyResponse(boolean enabled, String publicKey) {
}
