/** Mini-app dédiée, déployée séparément d'assur-zen-core (confirmé par le backend le 2026-09-18 :
 * /suivi/vignette et /suivi-sandbox/vignette ne sont pas un reverse-proxy vers la SPA principale).
 * Ces types sont volontairement dupliqués depuis
 * assur-zen-core/src/app/core/vignettes-moto/models/vignette-moto.model.ts (sous-ensemble
 * strictement nécessaire à l'endpoint public) plutôt que partagés via un chemin TS inter-projets —
 * ce déploiement ne doit dépendre d'aucun module interne de la SPA authentifiée. En cas
 * d'évolution du contrat `SuiviPublicVignetteResponse` côté backend, mettre à jour les deux
 * copies. */

export type VignetteMotoStatut =
  | 'BROUILLON'
  | 'EN_ATTENTE_PAIEMENT'
  | 'PAYEE'
  | 'EN_TRAITEMENT'
  | 'EN_EDITION'
  | 'EDITEE'
  | 'EN_LIVRAISON'
  | 'LIVREE'
  | 'ANNULEE'
  | 'REJETEE';

export interface SuiviPublicHistoriqueEntry {
  statut: VignetteMotoStatut;
  libelle: string;
  date: string;
}

/** GET {apiUrl}/vignettes-moto/demandes/suivi-public?token=... — 404 générique si le token est
 * inconnu, expiré ou révoqué (jamais distingué, volontaire côté sécurité). Réponse
 * délibérément minimale : pas de montant, téléphone, document, numéro de police/immatriculation. */
export interface SuiviPublicVignetteResponse {
  reference: string;
  statut: VignetteMotoStatut;
  libelleStatut: string;
  motifRejet: string | null;
  dateDerniereMiseAJour: string;
  historique: SuiviPublicHistoriqueEntry[];
}
