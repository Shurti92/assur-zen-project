/** Contrat : docs/api-contracts/admin-mobile.md du gateway (§1 Comptes clients). */

export interface MobileAccount {
  userId: number;
  phone: string;
  fullName: string;
  active: boolean;
  createdAt: string;
  linkedInsuredCount: number;
  /** Référence de la demande de suppression en attente, si elle existe. */
  pendingDeletionReference?: string | null;
  /** true dès que l'assuré s'est connecté au moins une fois (sinon : invitation envoyée). */
  activated?: boolean;
}

export interface MobileAccountPage {
  items: MobileAccount[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LinkedInsured {
  insuredId: number;
  name: string;
  phone: string;
}

export interface MobileAccountDetail {
  account: MobileAccount;
  linkedInsureds: LinkedInsured[];
}

export interface CreateMobileAccountRequest {
  insuredId: number;
  phone?: string;
  sendInvitation?: boolean;
}
