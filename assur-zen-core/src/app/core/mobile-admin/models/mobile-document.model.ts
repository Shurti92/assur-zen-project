/** Contrat : documents des contrats et validation (gateway, /admin/mobile, rôle ADMIN). */

export type MobileDocumentStatus = 'AVAILABLE' | 'PENDING_REVIEW' | 'REJECTED';
export type MobileDocumentSource = 'BACKOFFICE' | 'CLIENT';

export interface MobileContractDocument {
  id: number;
  contractId: number;
  type: string;
  category: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: MobileDocumentStatus;
  source: MobileDocumentSource;
  uploadedBy?: string | null;
  uploadedAt: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  rejectReason?: string | null;
}

export interface MobilePendingDocument {
  document: MobileContractDocument;
  policyNumber: string;
  insuredName: string;
}

export interface MobilePendingDocumentPage {
  items: MobilePendingDocument[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Types proposés à l'ajout (rubriques d'abord, puis types historiques utiles). */
export const DOCUMENT_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'CONTRAT_ASSURANCE', label: "Contrat d'assurance" },
  { value: 'RECU_ACHAT', label: "Reçu d'achat" },
  { value: 'ATTESTATION_ASSURANCE', label: "Attestation d'assurance" },
  { value: 'PIECE_IDENTITE', label: "Pièce d'identité" },
  { value: 'CARTE_GRISE', label: 'Carte grise' },
  { value: 'AUTRE', label: 'Autre' },
];

const TYPE_LABEL: Record<string, string> = {
  ...Object.fromEntries(DOCUMENT_TYPE_OPTIONS.map((o) => [o.value, o.label])),
  ATTESTATION: 'Attestation',
  CONTRAT: 'Contrat',
  CONDITIONS_PARTICULIERES: 'Conditions particulières',
};

export const documentTypeLabel = (type: string): string => TYPE_LABEL[type] ?? type;

export const DOCUMENT_STATUS_LABEL: Record<MobileDocumentStatus, string> = {
  AVAILABLE: 'Disponible',
  PENDING_REVIEW: 'À valider',
  REJECTED: 'Refusé',
};

export const DOCUMENT_STATUS_VARIANT: Record<MobileDocumentStatus, string> = {
  AVAILABLE: 'success',
  PENDING_REVIEW: 'warning',
  REJECTED: 'danger',
};

export const DOCUMENT_SOURCE_LABEL: Record<MobileDocumentSource, string> = {
  BACKOFFICE: 'Back-office',
  CLIENT: 'Client (mobile)',
};

export const formatDocumentSize = (bytes: number): string =>
  bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} Mo` : `${Math.max(1, Math.round(bytes / 1024))} Ko`;
