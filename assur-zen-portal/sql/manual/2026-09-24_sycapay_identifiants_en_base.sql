-- Identifiants SycaPay modifiables depuis le back-office, sans redemarrage (2026-09-24).
-- Cree les 3 lignes SANS VALEUR : les valeurs se saisissent UNIQUEMENT depuis le back-office
-- (module Configuration), qui les chiffre en AES-256-GCM avant stockage. Ne JAMAIS inserer de valeur
-- ici ni directement en base : elle serait en clair ou chiffree avec une mauvaise cle.
--
-- PREREQUIS : APP_CONFIG_ENCRYPTION_SECRET (>= 32 caracteres) et APP_CONFIG_ENCRYPTION_SALT (hexadecimal)
-- fournis par l'environnement du serveur. Sans eux, la saisie est refusee (HTTP 409) et les
-- identifiants restent lus dans SYCAPAY_MERCHANT_ID / SYCAPAY_API_KEY / SYCAPAY_SECRET_KEY.
-- Tant qu'une ligne est vide, la variable d'environnement correspondante reste utilisee : ce script
-- ne change rien au comportement tant que rien n'est saisi.
--
-- is_sensitive = 1 : valeur jamais renvoyee par l'API (masquee "********"), jamais repassable en clair.
-- Echoue sans effet si les lignes existent deja (cle unique sur config_key).

INSERT INTO t_assur_zen_dynamic_configuration
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
