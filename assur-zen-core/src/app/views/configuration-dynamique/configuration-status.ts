import {
  ConfigurationCategory,
  ConfigurationEnvironment,
  ConfigurationValueType,
} from '../../core/configurations/models/configuration.model';

export const CONFIG_VALUE_TYPE_ORDER: ConfigurationValueType[] = [
  'STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'JSON', 'LIST', 'ENUM', 'URL', 'DURATION',
];

export const CONFIG_VALUE_TYPE_LABEL: Record<ConfigurationValueType, string> = {
  STRING: 'Texte',
  INTEGER: 'Entier',
  LONG: 'Entier long',
  DECIMAL: 'Décimal',
  BOOLEAN: 'Booléen',
  DATE: 'Date',
  DATETIME: 'Date et heure',
  JSON: 'JSON',
  LIST: 'Liste',
  ENUM: 'Énumération',
  URL: 'URL',
  DURATION: 'Durée',
};

export const CONFIG_CATEGORY_ORDER: ConfigurationCategory[] = [
  'GENERAL', 'SECURITY', 'AUTHENTICATION', 'CONTRACT', 'SUBSCRIPTION', 'PAYMENT',
  'CLAIM', 'CUSTOMER', 'MARKETING', 'LOYALTY', 'REFERRAL', 'NOTIFICATION',
  'DOCUMENT', 'SYSTEM', 'OTHER',
];

export const CONFIG_CATEGORY_LABEL: Record<ConfigurationCategory, string> = {
  GENERAL: 'Général',
  SECURITY: 'Sécurité',
  AUTHENTICATION: 'Authentification',
  CONTRACT: 'Contrat',
  SUBSCRIPTION: 'Souscription',
  PAYMENT: 'Paiement',
  CLAIM: 'Sinistre',
  CUSTOMER: 'Client',
  MARKETING: 'Marketing',
  LOYALTY: 'Fidélisation',
  REFERRAL: 'Parrainage',
  NOTIFICATION: 'Notification',
  DOCUMENT: 'Document',
  SYSTEM: 'Système',
  OTHER: 'Autre',
};

export const CONFIG_ENVIRONMENT_ORDER: ConfigurationEnvironment[] = ['DEV', 'TEST', 'UAT', 'PREPROD', 'PRODUCTION'];

export const CONFIG_ENVIRONMENT_LABEL: Record<ConfigurationEnvironment, string> = {
  DEV: 'Développement',
  TEST: 'Test',
  UAT: 'UAT',
  PREPROD: 'Pré-production',
  PRODUCTION: 'Production',
};

export const CONFIG_ENVIRONMENT_VARIANT: Record<ConfigurationEnvironment, string> = {
  DEV: 'neutral',
  TEST: 'info',
  UAT: 'warning',
  PREPROD: 'warning',
  PRODUCTION: 'danger',
};
