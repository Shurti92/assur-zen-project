export type ConfigurationValueType =
  | 'STRING' | 'INTEGER' | 'LONG' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
  | 'DATETIME' | 'JSON' | 'LIST' | 'ENUM' | 'URL' | 'DURATION';

export type ConfigurationCategory =
  | 'GENERAL' | 'SECURITY' | 'AUTHENTICATION' | 'CONTRACT' | 'SUBSCRIPTION' | 'PAYMENT'
  | 'CLAIM' | 'CUSTOMER' | 'MARKETING' | 'LOYALTY' | 'REFERRAL' | 'NOTIFICATION'
  | 'DOCUMENT' | 'SYSTEM' | 'OTHER';

export type ConfigurationEnvironment = 'DEV' | 'TEST' | 'UAT' | 'PREPROD' | 'PRODUCTION';

export interface DynamicConfigurationResponse {
  id: number;
  uuid: string;
  configKey: string;
  configValue: string | null;
  valueType: ConfigurationValueType;
  description: string | null;
  module: string | null;
  category: ConfigurationCategory | null;
  environment: ConfigurationEnvironment | null;
  defaultValue: string | null;
  validationRule: string | null;
  allowedValues: string | null;
  active: boolean;
  editable: boolean;
  sensitive: boolean;
  restartRequired: boolean;
  cacheEnabled: boolean;
  displayOrder: number | null;
  createdAt: string;
  updatedAt: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  version: number | null;
}

export interface DynamicConfigurationRequest {
  configKey: string;
  configValue?: string;
  valueType: ConfigurationValueType;
  description?: string;
  module?: string;
  category?: ConfigurationCategory;
  environment?: ConfigurationEnvironment;
  defaultValue?: string;
  validationRule?: string;
  allowedValues?: string;
  active?: boolean;
  editable?: boolean;
  sensitive?: boolean;
  restartRequired?: boolean;
  cacheEnabled?: boolean;
  displayOrder?: number;
  /** Ajouté au DTO côté backend le 2026-08-21 — comble le manque documenté précédemment
   * (aucun motif n'était transmis à la modification d'une config sensible). */
  motif?: string;
}

export interface ConfigurationPage {
  content: DynamicConfigurationResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ConfigurationFilter {
  q?: string;
  module?: string;
  category?: ConfigurationCategory;
  valueType?: ConfigurationValueType;
  environment?: ConfigurationEnvironment;
  active?: boolean;
  editable?: boolean;
  sensitive?: boolean;
}
