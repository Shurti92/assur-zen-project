export type GedDocumentStatus = 'UPLOADED' | 'VALIDATED' | 'REJECTED' | 'ARCHIVED' | 'DELETED';
export type GedConfidentialityLevel = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
export type GedStorageProvider = 'LOCAL' | 'S3' | 'AZURE_BLOB' | 'MINIO';

/** Valeurs déduites des routes de commodité livrées côté backend (/ged/clients, /ged/contracts,
 * /ged/vehicles, /ged/fleets) — aucune liste fermée n'est imposée par l'API (entityType est une
 * simple chaîne libre côté GedDocumentLinkRequest). CLAIM (Sinistre) n'a pas de route de
 * commodité dédiée mais a été testé et accepté en direct via POST .../links + retrouvé via
 * /ged/documents/search?entityType=CLAIM — voir GedDocumentService.listByClaim(). */
export type GedEntityType = 'CLIENT' | 'CONTRACT' | 'VEHICLE' | 'FLEET' | 'CLAIM';

export interface GedDocumentResponse {
  id: number;
  uuid: string;
  documentNumber: string;
  documentTypeId: number;
  documentTypeCode: string;
  documentTypeNom: string;
  categoryId: number | null;
  categoryCode: string | null;
  categoryNom: string | null;
  title: string;
  description: string | null;
  status: GedDocumentStatus;
  confidentialityLevel: GedConfidentialityLevel;
  source: string | null;
  rejectionReason: string | null;
  checksumSha256: string | null;
  currentVersionNumber: number;
  duplicateOfDocumentId: number | null;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  archivedAt: string | null;
  archivedBy: string | null;
}

export interface GedDocumentPage {
  content: GedDocumentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Métadonnées envoyées en query params à l'upload (POST /ged/documents) — le fichier lui-même
 * voyage seul dans le corps multipart (champ "file"), pas dans cet objet. */
export interface GedDocumentUploadMeta {
  documentTypeId: number;
  categoryId?: number;
  title: string;
  description?: string;
  entityType?: GedEntityType | string;
  entityId?: number;
  confidentialityLevel?: GedConfidentialityLevel;
  source?: string;
}

/** Modification des métadonnées uniquement (PUT /ged/documents/{id}) — jamais le fichier, qui
 * passe par l'ajout d'une nouvelle version. */
export interface UpdateGedDocumentRequest {
  title: string;
  description?: string;
  categoryId?: number;
  confidentialityLevel?: GedConfidentialityLevel;
}

export interface RejectGedDocumentRequest {
  reason: string;
}

export interface GedDocumentVersionResponse {
  id: number;
  versionNumber: number;
  fileName: string;
  originalFileName: string;
  mimeType: string;
  extension: string;
  fileSizeBytes: number;
  checksumSha256: string;
  storageProvider: GedStorageProvider;
  createdAt: string;
  createdBy: string | null;
}

export interface GedDocumentLinkRequest {
  entityType: GedEntityType | string;
  entityId: number;
  relationshipType?: string;
}

export interface GedDocumentLinkResponse {
  id: number;
  entityType: string;
  entityId: number;
  relationshipType: string | null;
  createdAt: string;
  createdBy: string | null;
}

/** Filtres de GET /ged/documents/search — pagination serveur classique. documentType/category
 * portent le CODE (documentTypeCode/categoryCode), pas l'id (asymétrie confirmée côté backend
 * par rapport aux ids utilisés à l'upload). */
export interface GedDocumentSearchFilters {
  documentNumber?: string;
  documentType?: string;
  category?: string;
  status?: GedDocumentStatus;
  entityType?: string;
  entityId?: number;
  createdBy?: string;
  dateFrom?: string;
  dateTo?: string;
  confidentialityLevel?: GedConfidentialityLevel;
}
