export type VignetteMotoTypeDemande = 'NOUVELLE_DEMANDE' | 'RENOUVELLEMENT';

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

export type VignetteMotoCategorieAge = '1_4_ANS' | '5_10_ANS' | '11_ANS_ET_PLUS';

export type VignetteMotoCategorieCylindree = 'MOINS_125_CM3' | '125_CM3_ET_PLUS';

/** Types acceptés par POST .../demandes/{id}/documents?typeCode=... (créés côté GED via
 * /ged/document-types avant la première utilisation — voir le contrat). PHOTO_MOTO/PHOTO_CHASSIS/
 * ASSURANCE confirmés le 2026-09-19, requis uniquement pour NOUVELLE_DEMANDE. */
export type VignetteMotoDocumentTypeCode =
  | 'CNI_ASSURE'
  | 'RECU_ACHAT_MOTO'
  | 'ANCIENNE_VIGNETTE'
  | 'PHOTO_MOTO'
  | 'PHOTO_CHASSIS'
  | 'ASSURANCE';

/** POST /vignettes-moto/tarification/simuler — aucune création, juste un calcul.
 * partenaireApporteurId/commercialApporteurId confirmés le 2026-09-19 : mutuellement exclusifs
 * (400 si les deux), id inexistant → 404 dès la simulation. */
export interface SimulationTarifVignetteMotoRequest {
  datePremiereMiseCirculation: string;
  cylindree: number;
  /** Code de zone (ex. "ABIDJAN"), pas un texte de ville — voir GET /vignettes-moto/livraisons. */
  zoneLivraison: string;
  partenaireApporteurId?: number;
  commercialApporteurId?: number;
}

export interface SimulationTarifVignetteMotoResponse {
  categorieAge: VignetteMotoCategorieAge;
  ageMoto: number;
  categorieCylindree: VignetteMotoCategorieCylindree;
  montantVignette: number;
  fraisOperation: number;
  fraisLivraison: number;
  fraisAccessoires: number;
  montantTotal: number;
  /** null si pas d'apporteur — déjà déduit de montantTotal, à afficher explicitement (ex. "Réduction
   * apporteur : -500 FCFA"). Confirmé 2026-09-19 : 500 FCFA zone ABIDJAN, 1000 FCFA HORS_ABIDJAN. */
  reductionApporteur: number | null;
}

/** POST /vignettes-moto/demandes — champs plats (pas d'objets assure{}/moto{} imbriqués),
 * pour rester cohérent avec le reste de l'API (confirmé par le contrat backend). */
export interface DemandeVignetteMotoRequest {
  typeDemande: VignetteMotoTypeDemande;
  nomAssure: string;
  prenomsAssure: string;
  /** Obligatoire depuis la livraison du suivi par lien SMS (2026-09-17) — sans ce numéro,
   * aucun SMS de suivi (création, changements de statut, lien perdu) ne peut être envoyé. */
  telephoneAssure: string;
  /** Optionnel (2026-09-19) — purement informatif/stocké, aucun SMS automatique dessus. */
  telephoneAssureSecondaire?: string;
  adresseLivraison: string;
  /** Ville en texte libre, pour affichage/adresse — distincte de zoneLivraison (le code tarifaire). */
  villeLivraison: string;
  zoneLivraison: string;
  marqueMoto: string;
  modeleMoto: string;
  numeroChassisMoto: string;
  cylindreeMoto: number;
  datePremiereMiseCirculation: string;
  /** Mutuellement exclusifs (400 si les deux) — voir vignette_commission_apporteur en mémoire projet. */
  partenaireApporteurId?: number;
  commercialApporteurId?: number;
}

/** Les montants sont calculés et figés à la création — ils ne bougent plus jamais, même si la
 * grille tarifaire change ensuite (testé explicitement côté backend). */
export interface DemandeVignetteMotoResponse {
  id: number;
  uuid: string;
  reference: string;
  typeDemande: VignetteMotoTypeDemande;
  statut: VignetteMotoStatut;
  nomAssure: string;
  prenomsAssure: string;
  telephoneAssure: string;
  telephoneAssureSecondaire: string | null;
  adresseLivraison: string;
  villeLivraison: string;
  zoneLivraisonCode: string;
  marqueMoto: string;
  modeleMoto: string;
  numeroChassisMoto: string;
  cylindreeMoto: number;
  datePremiereMiseCirculation: string;
  categorieAgeAppliquee: VignetteMotoCategorieAge;
  categorieCylindreeAppliquee: VignetteMotoCategorieCylindree;
  ageMotoCalcule: number;
  tarifVignetteMotoId: number;
  montantVignette: number;
  fraisOperation: number;
  fraisLivraison: number;
  fraisAccessoires: number;
  montantTotal: number;
  partenaireApporteurId: number | null;
  partenaireApporteurNom: string | null;
  commercialApporteurId: number | null;
  commercialApporteurNom: string | null;
  /** null si pas d'apporteur — déjà déduit de montantTotal. */
  reductionApporteur: number | null;
  motifRejet: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DemandeVignetteMotoPage {
  content: DemandeVignetteMotoResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RejeterDemandeVignetteMotoRequest {
  motif: string;
}

// ================================================================ SUIVI D'ÉVOLUTION + LIEN SMS (livré 2026-09-17) ================================================================

/** GET /vignettes-moto/demandes/{id}/historique — endpoints admin/gestionnaire, jamais exposés au
 * créateur simple de la demande (contrairement à la liste/détail, scopés owner depuis le
 * durcissement IDOR du 2026-09-12). Masquage d'affichage ADMIN, comme partout ailleurs dans ce
 * module — le vrai rempart reste le 403 backend. */
export interface HistoriqueDemandeVignetteMotoResponse {
  ancienStatut: VignetteMotoStatut | null;
  nouveauStatut: VignetteMotoStatut;
  motif: string | null;
  referenceOperation: string | null;
  auteurNom: string | null;
  notificationEnvoyee: boolean;
  createdAt: string;
}

/** POST /vignettes-moto/demandes/{id}/statut (ADMIN) — ne fait progresser que vers PAYEE,
 * EN_TRAITEMENT, EN_EDITION, EDITEE, EN_LIVRAISON, LIVREE, ou REJETEE (à n'importe quelle étape).
 * BROUILLON/EN_ATTENTE_PAIEMENT/ANNULEE restent gérés par soumettre()/annuler(). 409 si la
 * transition n'est pas autorisée par la matrice de statuts côté backend. */
export interface ChangerStatutDemandeVignetteMotoRequest {
  statut: VignetteMotoStatut;
  motif?: string;
  referenceOperation?: string;
}

/** POST /vignettes-moto/demandes/{id}/lien-suivi/regeneration (ADMIN) — envoie aussi un SMS au
 * client avec le nouveau lien (perte du lien, ou lien expiré/révoqué). */
export interface LienSuiviVignetteResponse {
  tokenBrut: string;
  lienComplet: string;
  dateExpiration: string;
}

/** POST /vignettes-moto/demandes/{id}/lien-suivi/revocation (ADMIN) — invalide le lien actif
 * (ex. compromission) ; un motif est requis. */
export interface RevoquerLienSuiviRequest {
  motif: string;
}

/** Une entrée de l'historique tel qu'exposé par l'endpoint PUBLIC (pas d'auteur/référence
 * d'opération — réponse volontairement minimale, voir SuiviPublicVignetteResponse). */
export interface SuiviPublicHistoriqueEntry {
  statut: VignetteMotoStatut;
  libelle: string;
  date: string;
}

/** GET /vignettes-moto/demandes/suivi-public?token=... — endpoint PUBLIC (aucune authentification),
 * appelé depuis la page de suivi client. 404 générique si le token est inconnu, expiré ou révoqué
 * (jamais de distinction entre ces 3 cas, volontaire côté sécurité) : traiter comme "lien
 * invalide/expiré" côté UI, sans essayer de différencier. Réponse deliberement minimale : pas de
 * montant, pas de téléphone, pas de document. */
export interface SuiviPublicVignetteResponse {
  reference: string;
  statut: VignetteMotoStatut;
  libelleStatut: string;
  motifRejet: string | null;
  dateDerniereMiseAJour: string;
  historique: SuiviPublicHistoriqueEntry[];
}

// ================================================================ COMMISSION APPORTEUR (confirmé 2026-09-19) ================================================================

export type VignetteCommissionStatut = 'pending' | 'paid';

/** Identique sur les 3 GET commission (liste, par partenaire, par commercial). Exactement un de
 * (partenaireId+partenaireNom) OU (commercialId+commercialNom) est non-null, jamais les deux.
 * notificationEnvoyee est TOUJOURS null en lecture — rempli seulement dans la réponse de POST /payer. */
export interface VignetteCommissionResponse {
  id: number;
  demandeVignetteMotoId: number;
  demandeVignetteMotoReference: string;
  partenaireId: number | null;
  partenaireNom: string | null;
  commercialId: number | null;
  commercialNom: string | null;
  montant: number;
  statut: VignetteCommissionStatut;
  paidByUserId: number | null;
  paidByUsername: string | null;
  /** Référence de PAIEMENT (fournie à payer()), pas la référence de la demande. */
  reference: string | null;
  paidAt: string | null;
  createdAt: string;
  notificationEnvoyee: boolean | null;
}

export interface VignetteCommissionPage {
  content: VignetteCommissionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** POST /vignettes-moto/commissions/payer (ADMIN) — marque payé + SMS à l'apporteur. */
export interface PayerVignetteCommissionsRequest {
  commissionIds: number[];
  reference: string;
}
