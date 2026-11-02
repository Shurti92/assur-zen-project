export interface AuditEntry {
  id: number;
  code: string;
  creation: string;
  direction: string;
  exceptionMessage: string | null;
  flux: string | null;
  numeroContrat: string | null;
  operation: string;
  route: string;
  username: string;
  durationMs: number;
  ipAddress: string;
  statusCode: number | null;
  userId: number;
}

export interface AuditPage {
  content: AuditEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
