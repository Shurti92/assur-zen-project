export type FleetType = 'AUTO' | 'MOTO' | 'MIXED';
export type FleetStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | 'EXPIRED';
export type VehiculeType = 'AUTO' | 'MOTO';
export type VehiculeStatut = 'ACTIF' | 'HORS_SERVICE' | 'VENDU';
export type FleetVehicleStatus = 'ACTIVE' | 'REMOVED' | 'TRANSFERRED' | 'REPLACED';
export type FleetMovementType = 'ADD' | 'REMOVE' | 'REPLACE' | 'TRANSFER';
export type FleetImportFormat = 'CSV' | 'XLSX';
export type FleetImportStatus =
  | 'UPLOADED'
  | 'VALIDATING'
  | 'READY'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface FleetRequest {
  fleetNumber?: string;
  /** Id de l'assuré (client) propriétaire de la flotte — même référentiel que VehiculeRequest.proprietaireId. */
  clientId: number;
  name: string;
  description?: string;
  fleetType: FleetType;
  /** Produit d'assurance de la flotte — optionnel côté backend, ajouté le 2026-09-02. */
  produitId?: number;
  startDate?: string;
  endDate?: string;
  managerName?: string;
  managerPhone?: string;
  managerEmail?: string;
}

export interface FleetResponse {
  id: number;
  fleetNumber: string;
  clientId: number;
  clientName: string;
  name: string;
  description: string | null;
  fleetType: FleetType;
  produitId: number | null;
  produitNom: string | null;
  status: FleetStatus;
  startDate: string | null;
  endDate: string | null;
  managerName: string | null;
  managerPhone: string | null;
  managerEmail: string | null;
  totalVehicles: number;
  activeVehicles: number;
  createdAt: string;
  updatedAt: string;
}

export interface FleetPage {
  content: FleetResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface VehiculeRequest {
  /** Optionnelle depuis le 2026-09-13 : une moto sans plaque encore attribuée peut être
   * enregistrée via numeroChassis seul — le backend exige l'un des deux. */
  immatriculation?: string;
  type: VehiculeType;
  marque: string;
  modele: string;
  numeroChassis?: string;
  dateMiseEnCirculation?: string;
  puissanceFiscale?: number;
  /** Cylindrée en cm³ — pertinent surtout pour une moto (2026-09-13). */
  cylindree?: number;
  couleur?: string;
  proprietaireId: number;
  statut?: VehiculeStatut;
}

export interface VehiculeResponse {
  id: number;
  immatriculation: string | null;
  type: VehiculeType;
  marque: string;
  modele: string;
  numeroChassis: string | null;
  dateMiseEnCirculation: string | null;
  puissanceFiscale: number | null;
  cylindree: number | null;
  couleur: string | null;
  proprietaireId: number;
  proprietaireName: string;
  statut: VehiculeStatut;
  currentFleetId: number | null;
  currentFleetNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface VehiculePage {
  content: VehiculeResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FleetVehicleResponse {
  id: number;
  fleetId: number;
  fleetNumber: string;
  /** Nullable depuis le 2026-09-13 : un véhicule retiré de toute flotte peut ensuite être
   * supprimé (DELETE /vehicules/{id}) — sa ligne d'historique reste, ces 3 champs passent
   * alors à null plutôt que de faire échouer tout l'appel (comportement corrigé côté backend). */
  vehiculeId: number | null;
  immatriculation: string | null;
  vehiculeType: VehiculeType | null;
  status: FleetVehicleStatus;
  dateAdded: string;
  dateRemoved: string | null;
  /** Confirmé par le backend le 2026-09-13 : renseigné uniquement sur la réponse d'addVehicle()
   * (SMS d'incorporation), toujours null sur retrait/remplacement/transfert/liste. */
  notificationEnvoyee: boolean | null;
}

export interface FleetVehiclePage {
  content: FleetVehicleResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AddVehicleRequest {
  vehiculeId: number;
  reason?: string;
}

export interface RemoveVehicleRequest {
  vehiculeId: number;
  reason?: string;
}

export interface ReplaceVehicleRequest {
  oldVehiculeId: number;
  newVehiculeId: number;
  reason?: string;
}

export interface TransferVehicleRequest {
  destinationFleetId: number;
  reason?: string;
}

/** POST /fleets/{fleetId}/contracts/attach (2026-09-14) — rattache un contrat mono déjà
 * existant (souscrit hors flotte, sans véhicule) à une flotte. Ajoute le véhicule à la flotte
 * au passage s'il n'y est pas encore (mêmes garde-fous que AddVehicleRequest). Réservé admin. */
export interface AttachContractRequest {
  contractId: number;
  vehiculeId: number;
  reason?: string;
}

export interface FleetMovementResponse {
  id: number;
  fleetId: number;
  /** Nullable depuis le 2026-09-13 : le véhicule peut avoir été supprimé depuis (voir
   * FleetVehicleResponse) — la ligne de mouvement reste, ces 2 champs passent à null. */
  vehiculeId: number | null;
  immatriculation: string | null;
  movementType: FleetMovementType;
  movementDate: string;
  reason: string | null;
  oldVehiculeId: number | null;
  oldImmatriculation: string | null;
  newVehiculeId: number | null;
  newImmatriculation: string | null;
  sourceFleetId: number | null;
  sourceFleetNumber: string | null;
  destinationFleetId: number | null;
  destinationFleetNumber: string | null;
  contractId: number | null;
  contractPolicyNumber: string | null;
  performedById: number | null;
  performedByName: string | null;
  createdAt: string;
}

export interface FleetMovementPage {
  content: FleetMovementResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** `status` reprend les 4 valeurs de ContractStatus (core/contracts/models/contract.model.ts) —
 * gardé en string libre ici pour ne pas créer de dépendance croisée entre les deux modules. */
export interface FleetContractResponse {
  contractId: number;
  policyNumber: string;
  produitNom: string | null;
  /** Nullable depuis le 2026-09-13, même protection que FleetVehicleResponse/FleetMovementResponse
   * pour un véhicule depuis supprimé — mais endpoint réputé buggé (500 reproduit en direct le
   * 2026-09-13 sur une flotte avec un véhicule supprimé, signalé au backend, pas encore confirmé
   * corrigé). Pas de champ vehiculeType ici (contrairement à FleetVehicleResponse). */
  vehiculeId: number | null;
  immatriculation: string | null;
  dateEffet: string | null;
  dateEcheance: string | null;
  premium: number;
  status: string;
}

export interface FleetImportResponse {
  id: number;
  fleetId: number;
  fileName: string;
  format: FleetImportFormat;
  status: FleetImportStatus;
  totalRows: number | null;
  validRows: number | null;
  invalidRows: number | null;
  duplicateRows: number | null;
  successCount: number | null;
  errorCount: number | null;
  uploadedBy: string | null;
  /** Confirmé par le backend le 2026-09-13 : renseigné uniquement sur la réponse de confirm(). */
  notificationEnvoyee: boolean | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface FleetImportPage {
  content: FleetImportResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FleetImportErrorResponse {
  id: number;
  ligneNumero: number;
  colonneEnErreur: string | null;
  valeurBrute: string | null;
  message: string;
}

/** Un rappel SMS planifié/envoyé pour un contrat (avis d'échéance 45/15/5j ou fin de contrat) —
 * même vocabulaire que ContractMessageResponse (core/contracts), gardé en types locaux ici pour
 * ne pas créer de dépendance croisée entre les modules Flotte et Contrats. */
export interface NotificateurSmsResponse {
  id: number;
  contractId: number;
  telephoneAssure: string;
  message: string;
  typeMessage: string;
  dateEnvoi: string | null;
  statutEnvoi: string;
  responseProvider: string | null;
}

/** GET /fleets/{fleetId}/echeances (2026-09-13) — contrats des véhicules actuellement incorporés
 * dans la flotte, avec leurs rappels SMS d'échéance imbriqués. */
export interface FleetEcheanceResponse {
  contractId: number;
  policyNumber: string;
  status: string;
  vehiculeId: number;
  immatriculation: string | null;
  vehiculeMarque: string;
  vehiculeModele: string;
  vehiculeType: VehiculeType;
  dateEffet: string | null;
  dateEcheance: string | null;
  rappels: NotificateurSmsResponse[];
}

export interface FleetImportErrorPage {
  content: FleetImportErrorResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
