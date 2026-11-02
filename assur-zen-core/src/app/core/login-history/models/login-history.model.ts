// Contrat confirmé via /v3/api-docs le 2026-08-24 (module "Historique de connexion",
// endpoints GET /historique-connexions et GET /historique-connexions/statistiques,
// réservés admin). Le schéma correspond exactement au contrat annoncé par le backend :
// PageResponseLoginHistoryResponse expose bien page/size/totalElements/totalPages, comme
// les autres PageResponse* du projet. Tous les champs de LoginHistoryResponse sont typés
// non-nullables côté OpenAPI mais traités défensivement côté front (valeur affichée "—" si
// vide/null au runtime).

export type LoginDeviceType = 'DESKTOP' | 'MOBILE' | 'TABLET' | 'BOT' | 'INCONNU';

export interface LoginHistoryResponse {
  id: number;
  userId: number | null;
  username: string | null;
  partenaireId: number | null;
  ip: string | null;
  deviceType: LoginDeviceType | null;
  browser: string | null;
  os: string | null;
  country: string | null;
  city: string | null;
  isp: string | null;
  dateHeure: string | null;
}

export interface LoginHistoryPage {
  content: LoginHistoryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LoginHistoryStatsResponse {
  connexionsAujourdHui: number;
  connexionsCetteSemaine: number;
  connexionsCeMois: number;
}

export interface LoginHistoryFilter {
  partenaireId?: number;
  userId?: number;
  dateDebut?: string;
  dateFin?: string;
}
