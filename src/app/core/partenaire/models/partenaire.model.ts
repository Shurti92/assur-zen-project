export type TypePartenaire = 'DIRECT' | 'AGENT' | 'APPORTEUR';

export type FrequencePaiementCommission = 'IMMEDIAT' | 'HEBDOMADAIRE' | 'QUINZAINE' | 'MENSUELLE' | 'A_LA_DEMANDE';

/** ids du référentiel /type-profiles — pas d'enum côté backend (libellés en texte libre),
 * mais ces 3 valeurs sont celles sur lesquelles l'app back-office prend des décisions
 * d'accès aujourd'hui. Voir NavPermissionsService (masquage menu) et AppAccessService
 * (blocage complet REPRESENTANT). */
export const TYPE_PROFILE_ADMIN_ID = 1;
export const TYPE_PROFILE_CO_FOUNDER_ID = 2;
export const TYPE_PROFILE_REPRESENTANT_ID = 5;

export interface PartenaireRequest {
  codePartenaire?: string;
  type: TypePartenaire;
  nom: string;
  prenoms: string;
  objectifProduction: number;
  profileId?: number | null;
  exerciceComptableId?: number | null;
  villePartenaireId?: number | null;
  active: boolean;
  /** FK vers /type-profiles. Quand le type pointe vers un profil avec `declencheCreationCompte`
   * (ex. REPRESENTANT), le backend crée automatiquement un compte de connexion mobile pour ce
   * partenaire — voir docs/api-contracts/creation-comptes-utilisateurs.md. */
  typeProfileId?: number | null;
  /** Numéro de connexion (username du compte auto-créé) — distinct de numeroWaveCommission. */
  telephone?: string | null;
  /** Numéro Wave sur lequel le partenaire reçoit ses commissions — peut différer de `telephone`. */
  numeroWaveCommission?: string | null;
  /** Purement informatif à ce stade — aucune automatisation de paiement côté backend
   * (pas d'API de paiement de commission). Stocké et affiché uniquement. */
  frequencePaiementCommission?: FrequencePaiementCommission | null;
  /** Seconde condition (en ET avec typeProfileId → REPRESENTANT) pour la création automatique
   * de compte. Si repassé à false sur un partenaire qui avait déjà un compte créé, le backend
   * désactive automatiquement le profil et le compte utilisateur associés. */
  accesActiviteCommerciale?: boolean;
  /** Active la prime d'objectif pour ce partenaire (voir core/prime-objectif/) — sans ce
   * flag à true, POST /primes-objectif renvoie 409. Défaut false. */
  primeObjectifActivee?: boolean;
}

export interface PartenaireResponse extends PartenaireRequest {
  id: number;
  /** Backend-computed sum of premiums on signed contracts for this partner. */
  objectifRealise: number;
  /** Backend-computed (objectifRealise / objectifProduction) * 100, null when no objectif is set. */
  tauxRealisation: number | null;
  exerciceComptableLibelle: string | null;
  villePartenaireNom: string | null;
  creation: string;
  lastUpdate: string;
  /** Présent uniquement sur la réponse de création/modification : indique si cette sauvegarde
   * vient de déclencher la création automatique d'un compte de connexion. */
  compteCree?: boolean;
  /** Message d'erreur si compteCree est false alors qu'une création était attendue
   * (ex. téléphone déjà utilisé comme identifiant par un autre compte). */
  compteCreationErreur?: string | null;
}

export interface PartenairePage {
  content: PartenaireResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Entrée de la timeline chronologique d'un partenaire (avenants + audit confondus) —
 * GET /partenaires/{id}/contracts/historique. `source` et `type` sont des chaînes libres
 * côté backend (pas d'enum fermé documenté au schéma), affichées telles quelles. */
export interface HistoriqueContratResponse {
  contractId: number;
  policyNumber: string;
  source: string;
  type: string;
  numeroAvenant?: number | null;
  motif?: string | null;
  description?: string | null;
  champsModifies?: string | null;
  date: string;
  auteurId?: number | null;
  auteur?: string | null;
}

export interface HistoriqueContratPage {
  content: HistoriqueContratResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Résumé agrégé par contrat des avenants d'un partenaire — GET /partenaires/{id}/contracts/avenants.
 * `dernierTypeAvenant` réutilise les 36 valeurs de AvenantType (voir core/avenants/models/avenant.model.ts). */
export interface ContractAvenantSummaryResponse {
  contractId: number;
  policyNumber: string;
  partenaireId: number;
  partenaireName: string;
  nombreAvenants: number;
  dernierNumeroAvenant: number;
  dernierTypeAvenant: string;
  dernierAvenantAt: string;
}

export interface ContractAvenantSummaryPage {
  content: ContractAvenantSummaryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Catégorie fermée proposée côté backend (voir docs/api-contracts/partenaire-documents.md) —
 * une valeur inconnue reste affichée telle quelle plutôt que de faire planter l'UI. */
export type PartenaireDocumentType = 'CONTRAT_APPORTEUR' | 'CNI' | 'RIB' | 'JUSTIFICATIF_DOMICILE' | 'AUTRE';

/** Document joint à la fiche partenaire (contrat apporteur, CNI, ...) —
 * GET/POST /partenaires/{id}/documents, voir docs/api-contracts/partenaire-documents.md.
 * Même schéma que SinistrePhotoResponse/ReclamationPieceJointeResponse, avec `type` en plus. */
export interface PartenaireDocumentResponse {
  id: number;
  partenaireId: number;
  type: PartenaireDocumentType;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
  /** Vérification par AssurZen (contrat espace-partenaire-ep2-2026-10-03.md §8) — un document envoyé
   * par le partenaire arrive EN_ATTENTE, un document joint par un administrateur est VALIDE d'office. */
  statut: PartenaireDocumentStatut;
  motifRefus: string | null;
  /** Date-heure de la validation ou du refus. */
  valideLe: string | null;
  validePar: string | null;
  /** AAAA-MM-JJ, null tant qu'aucune date n'a été saisie (§6 du même contrat). */
  dateExpiration: string | null;
  /** Calculé côté backend : true si dateExpiration est strictement avant aujourd'hui. */
  expire: boolean;
}

export type PartenaireDocumentStatut = 'EN_ATTENTE' | 'VALIDE' | 'REFUSE';

/** PATCH /partenaires/{id}/documents/{documentId}/validation — `motif` obligatoire pour un refus
 * (500 caractères maximum, sinon 400). Un refus notifie réellement le partenaire. */
export type PartenaireDocumentValidationRequest = { statut: 'VALIDE' } | { statut: 'REFUSE'; motif: string };
