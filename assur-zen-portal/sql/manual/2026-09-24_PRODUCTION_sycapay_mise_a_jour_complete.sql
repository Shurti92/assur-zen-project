-- =====================================================================================
-- PRODUCTION - mise a jour SycaPay complete (22 -> 24/09/2026), en UN script rejouable.
-- Contenu IDENTIQUE au script de recette (valide en recette le 2026-09-24), plus des controles
-- specifiques a la production (section 0 et fin de section 4).
--
-- ORDRE OBLIGATOIRE :
--   a) SAUVEGARDE de la base de production (mysqldump) - sans exception ;
--   b) setenv.bat de production complete (SPRING_PROFILES_ACTIVE=prod, APP_CONFIG_ENCRYPTION_*
--      PROPRES A LA PROD, SYCAPAY_*), JAMAIS de SYCAPAY_TEST_MODE_* ;
--   c) deploiement du WAR de production + UN premier demarrage reussi (Hibernate, ddl-auto=update,
--      cree t_assur_zen_sycapay_transactions, t_assur_zen_partenaire_notification et les colonnes) ;
--   d) execution de ce script.
--
-- En PRODUCTION, data.sql n'est JAMAIS rejoue (spring.sql.init.mode=never) : les deux modeles SMS
-- COMMISSION_DISPONIBLE / COMMISSION_GAGNEE ne viennent QUE de ce script (section 2).
-- REJOUABLE : INSERT IGNORE, ALTER ... MODIFY, UPDATE ... WHERE ... IS NULL.
-- SYCAPAY_ENABLED insere a 'false' : AUCUN paiement n'est active par ce script.
-- Aucun secret dans ce fichier.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 0) PRE-CONTROLE (lecture seule) - a lancer SEUL d'abord. Les 4 valeurs doivent valoir 1.
--    Si table_sycapay = 0 ou table_notification = 0 : le nouveau WAR n'a pas encore demarre -> STOP.
--    Si categorie_commission = 0 ou langue_fr = 0 : les modeles SMS seraient crees sans categorie
--    ou sans langue -> STOP et me le signaler avant d'aller plus loin.
-- -------------------------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_sycapay_transactions') AS table_sycapay,
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_partenaire_notification') AS table_notification,
    (SELECT COUNT(*) FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION') AS categorie_commission,
    (SELECT COUNT(*) FROM t_assur_zen_communication_language WHERE code = 'fr') AS langue_fr;

-- -------------------------------------------------------------------------------------
-- 1) Reglages SycaPay (Dynamic Configuration)
-- (source : 2026-09-22_sycapay_dynamic_configuration_seed.sql)
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO t_assur_zen_dynamic_configuration
    (uuid, config_key, config_value, value_type, description, module, category, environment,
     default_value, validation_rule, allowed_values, active, editable, is_sensitive, restart_required,
     cache_enabled, display_order, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (UUID(), 'SYCAPAY_ENABLED', 'false', 'BOOLEAN',
     'Active/desactive l''integration SycaPay (paiement des commissions commerciales via virement Wave). Necessite merchant-id/api-key/secret-key valides en variables d''environnement.',
     'SYCAPAY', 'PAYMENT', NULL, 'false', NULL, NULL, 1, 1, 0, 0, 1, 10, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_BASE_URL', 'https://www.sycaretail.com/delivery/v1', 'URL',
     'URL de base des APIs SycaPay (authentification/checktransferwave/getStatusByOrderID/checkcashoutsolde).',
     'SYCAPAY', 'PAYMENT', NULL, 'https://www.sycaretail.com/delivery/v1', NULL, NULL, 1, 1, 0, 0, 1, 20, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_PROVIDER', 'Wave', 'STRING',
     'Operateur mobile money cible (perimetre actuel : Wave uniquement, decision produit du 2026-09-22).',
     'SYCAPAY', 'PAYMENT', NULL, 'Wave', 'ENUM:Wave', NULL, 1, 1, 0, 0, 1, 30, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_CURRENCY', 'XOF', 'STRING',
     'Devise des virements SycaPay.',
     'SYCAPAY', 'PAYMENT', NULL, 'XOF', NULL, NULL, 1, 1, 0, 0, 1, 40, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_LANGUAGE', 'fr', 'STRING',
     'Langue transmise a SycaPay (authentification et virement).',
     'SYCAPAY', 'PAYMENT', NULL, 'fr', NULL, NULL, 1, 1, 0, 0, 1, 50, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_CONNECT_TIMEOUT_MS', '5000', 'LONG',
     'Timeout de connexion HTTP vers SycaPay (ms). Change fige dans le client HTTP a la construction du bean : redemarrage necessaire pour prendre effet.',
     'SYCAPAY', 'PAYMENT', NULL, '5000', 'MIN:1000;MAX:30000', NULL, 1, 1, 0, 1, 1, 60, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_READ_TIMEOUT_MS', '15000', 'LONG',
     'Timeout de lecture HTTP vers SycaPay (ms). Change fige dans le client HTTP a la construction du bean : redemarrage necessaire pour prendre effet.',
     'SYCAPAY', 'PAYMENT', NULL, '15000', 'MIN:1000;MAX:60000', NULL, 1, 1, 0, 1, 1, 70, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_TOKEN_TTL_SECONDS', '40', 'LONG',
     'Duree de vie du token d''authentification SycaPay (secondes), confirmee a 40s le 2026-09-22.',
     'SYCAPAY', 'PAYMENT', NULL, '40', 'MIN:5;MAX:600', NULL, 1, 1, 0, 0, 1, 80, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_TOKEN_EXPIRY_MARGIN_SECONDS', '5', 'LONG',
     'Marge de securite retranchee au TTL du token pour ne jamais l''utiliser expire de justesse.',
     'SYCAPAY', 'PAYMENT', NULL, '5', 'MIN:0;MAX:60', NULL, 1, 1, 0, 0, 1, 90, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_TYPE_TRANSFER', '1', 'STRING',
     'Champ "typetransfer" envoye a checktransferwave. Signification non documentee par SycaPay - confirme par le client comme valeur par defaut a NE PAS modifier sans instruction explicite.',
     'SYCAPAY', 'PAYMENT', NULL, '1', 'ENUM:1', NULL, 1, 1, 0, 0, 1, 100, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_BALANCE_CACHE_TTL_SECONDS', '20', 'LONG',
     'Duree de mise en cache du solde marchand pour le polling frontend (GET /sycapay/balance) - jamais utilisee par le flux de paiement, qui verifie toujours le solde en direct.',
     'SYCAPAY', 'PAYMENT', NULL, '20', 'MIN:0;MAX:300', NULL, 1, 1, 0, 0, 1, 110, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_CALLBACK_SUCCESS_URL', 'https://assurzen.ci/sycapay/callback/success', 'URL',
     'URL "urlsuccess" exigee par le contrat SycaPay - integration serveur-a-serveur, aucune redirection navigateur reelle ne s''y produit.',
     'SYCAPAY', 'PAYMENT', NULL, 'https://assurzen.ci/sycapay/callback/success', NULL, NULL, 1, 1, 0, 0, 1, 120, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_CALLBACK_CANCEL_URL', 'https://assurzen.ci/sycapay/callback/cancel', 'URL',
     'URL "urlcancel" exigee par le contrat SycaPay - integration serveur-a-serveur, aucune redirection navigateur reelle ne s''y produit.',
     'SYCAPAY', 'PAYMENT', NULL, 'https://assurzen.ci/sycapay/callback/cancel', NULL, NULL, 1, 1, 0, 0, 1, 130, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_MERCHANT_DISPLAY_NAME', 'AssurZen', 'STRING',
     'Nom du marchand transmis a SycaPay (connectuser/connecteduser sur checktransferwave).',
     'SYCAPAY', 'PAYMENT', NULL, 'AssurZen', NULL, NULL, 1, 1, 0, 0, 1, 140, NOW(6), NOW(6), 'system', 'system', 0, 0);

-- -------------------------------------------------------------------------------------
-- 2) Beneficiaire partenaire : colonnes facultatives, backfill, modeles SMS
-- (source : 2026-09-23_sycapay_partenaire_beneficiaire.sql)
-- -------------------------------------------------------------------------------------
ALTER TABLE t_assur_zen_sycapay_transactions
    MODIFY COLUMN commercial_id BIGINT NULL;

UPDATE t_assur_zen_sycapay_transactions
SET beneficiary_type = 'COMMERCIAL'
WHERE beneficiary_type IS NULL;

ALTER TABLE t_assur_zen_commission_payments
    MODIFY COLUMN paid_by_id BIGINT NULL;

INSERT IGNORE INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_DISPONIBLE', 'Commissions disponibles (rappel periodique)',
     'SMS de rappel envoye aux partenaires HEBDOMADAIRE (lundi), QUINZAINE (1er et 16) et MENSUELLE (1er) a 9h s''il leur reste des commissions a payer (PartenaireCommissionAutomationService.envoyerRappels).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : vous avez {{montant}} FCFA de commissions disponibles. Vous pouvez en demander le paiement depuis votre espace partenaire.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT IGNORE INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_GAGNEE', 'Nouvelle commission gagnee (partenaire A LA DEMANDE)',
     'SMS envoye a un partenaire A_LA_DEMANDE a chaque contrat signe (nouvelle commission gagnee).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : nouvelle commission de {{montant}} FCFA (contrat {{police}}). Total disponible : {{total}} FCFA. Demandez-en le paiement depuis votre espace partenaire.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- -------------------------------------------------------------------------------------
-- 3) Identifiants SycaPay en base (lignes vides, sensibles)
-- (source : 2026-09-24_sycapay_identifiants_en_base.sql)
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO t_assur_zen_dynamic_configuration
    (uuid, config_key, config_value, value_type, description, module, category, environment,
     default_value, validation_rule, allowed_values, active, editable, is_sensitive, restart_required,
     cache_enabled, display_order, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (UUID(), 'SYCAPAY_MERCHANT_ID', NULL, 'STRING',
     'Identifiant marchand SycaPay (X-SYCA-MERCHANDID). Sensible : chiffre en base, jamais affiche. Vide = variable d''environnement SYCAPAY_MERCHANT_ID.',
     'SYCAPAY', 'PAYMENT', NULL, NULL, NULL, NULL, 1, 1, 1, 0, 1, 1, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_API_KEY', NULL, 'STRING',
     'Cle d''API SycaPay (X-SYCA-APIKEY). Sensible : chiffree en base, jamais affichee. Vide = variable d''environnement SYCAPAY_API_KEY.',
     'SYCAPAY', 'PAYMENT', NULL, NULL, NULL, NULL, 1, 1, 1, 0, 1, 2, NOW(6), NOW(6), 'system', 'system', 0, 0),

    (UUID(), 'SYCAPAY_SECRET_KEY', NULL, 'STRING',
     'Cle secrete SycaPay (signature HMAC AUTHORIZATION). Sensible : chiffree en base, jamais affichee. Vide = variable d''environnement SYCAPAY_SECRET_KEY.',
     'SYCAPAY', 'PAYMENT', NULL, NULL, NULL, NULL, 1, 1, 1, 0, 1, 3, NOW(6), NOW(6), 'system', 'system', 0, 0);

-- -------------------------------------------------------------------------------------
-- 4) Verification (lecture seule) : doit afficher 17 cles SYCAPAY dont 3 sensibles,
--    SYCAPAY_ENABLED = false, et commercial_id / paid_by_id nullable = YES.
-- -------------------------------------------------------------------------------------
SELECT COUNT(*) AS cles_sycapay, SUM(is_sensitive) AS dont_sensibles,
       MAX(CASE WHEN config_key = 'SYCAPAY_ENABLED' THEN config_value END) AS sycapay_enabled
FROM t_assur_zen_dynamic_configuration WHERE config_key LIKE 'SYCAPAY%';

SELECT table_name, column_name, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_assur_zen_sycapay_transactions' AND column_name = 'commercial_id')
    OR (table_name = 't_assur_zen_commission_payments' AND column_name = 'paid_by_id'));

-- Controle production : les 2 modeles SMS doivent exister, ACTIF, avec categorie et langue.
SELECT code, statut, categorie_id IS NOT NULL AS avec_categorie, langue_id IS NOT NULL AS avec_langue
FROM t_assur_zen_communication_template
WHERE code IN ('COMMISSION_PAYEE', 'COMMISSION_DISPONIBLE', 'COMMISSION_GAGNEE', 'COMMISSION_COMMERCIALE_PAYEE');
