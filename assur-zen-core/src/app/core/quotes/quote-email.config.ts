/**
 * Envoi du devis par e-mail au prospect (POST /admin/quotes/{reference}/send-email).
 *
 * Activé le 2026-11-03 après le « API disponible » de la gateway (commit 77007bb). Repasser à `false`
 * masque tous les boutons, champs et colonnes d'envoi sans rien supprimer.
 */
export const QUOTE_EMAIL_ENABLED = true;
