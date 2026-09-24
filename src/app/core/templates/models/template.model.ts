export type TemplateCanal = 'SMS' | 'EMAIL' | 'WHATSAPP' | 'PUSH' | 'PDF';
export type TemplateStatut = 'BROUILLON' | 'ACTIF' | 'ARCHIVE';
export type TemplateVariableType = 'TEXTE' | 'NOMBRE' | 'DATE' | 'MONTANT';

export interface CommunicationTemplateResponse {
  id: number;
  uuid: string;
  code: string;
  nom: string;
  description: string | null;
  canal: TemplateCanal;
  categorieId: number;
  categorieLibelle: string;
  langueId: number;
  langueLibelle: string;
  objet: string | null;
  contenu: string;
  statut: TemplateStatut;
  maxCaracteres: number | null;
  active: boolean;
  variableCodes: string[];
  createdAt: string;
  updatedAt: string | null;
  createdBy: string;
  updatedBy: string | null;
}

export interface CommunicationTemplateRequest {
  code: string;
  nom: string;
  description?: string;
  canal: TemplateCanal;
  categorieId: number;
  langueId: number;
  objet?: string;
  contenu: string;
  maxCaracteres?: number;
  variableCodes?: string[];
  active?: boolean;
}

export interface CommunicationTemplatePatchRequest {
  nom?: string;
  description?: string;
  categorieId?: number;
  langueId?: number;
  objet?: string;
  contenu?: string;
  maxCaracteres?: number;
  variableCodes?: string[];
  active?: boolean;
}

export interface TemplateVariableResponse {
  id: number;
  code: string;
  libelle: string;
  description: string | null;
  typeDonnee: TemplateVariableType;
  active: boolean;
}

export interface TemplateVariableRequest {
  code: string;
  libelle: string;
  description?: string;
  typeDonnee: TemplateVariableType;
  active?: boolean;
}

export interface TemplateCategoryResponse {
  id: number;
  code: string;
  libelle: string;
  description: string | null;
  active: boolean;
}

export interface TemplateCategoryRequest {
  code: string;
  libelle: string;
  description?: string;
  active?: boolean;
}

export interface TemplatePreviewRequest {
  valeurs?: Record<string, string>;
}

export interface TemplatePreviewResponse {
  objetResolu: string | null;
  contenuResolu: string;
  longueur: number;
  depasseMaxCaracteres: boolean;
}

export interface TemplatePage {
  content: CommunicationTemplateResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
