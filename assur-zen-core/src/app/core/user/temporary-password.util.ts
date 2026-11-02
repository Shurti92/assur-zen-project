/** Évite les caractères ambigus (0/O, 1/l/I) pour rester lisible une fois affiché à l'écran. */
const CHARSET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%';

/**
 * Génère un mot de passe temporaire côté client. Nécessaire depuis que
 * `PATCH /users/{id}/reset-password` exige `newPassword` dans le corps de la requête
 * (le backend ne le génère plus lui-même pour cet endpoint — voir missing_backend_dependencies).
 */
export function generateTemporaryPassword(length = 12): string {
  const bytes = new Uint32Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => CHARSET[b % CHARSET.length]).join('');
}
