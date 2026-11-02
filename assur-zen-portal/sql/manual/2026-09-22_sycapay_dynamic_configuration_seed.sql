-- Integration SycaPay - reglages operationnels rendus modifiables depuis le back-office via le
-- Dynamic Configuration Engine deja existant (t_assur_zen_dynamic_configuration), sans redeploiement.
-- merchantId/apiKey/secretKey restent EXCLUSIVEMENT dans les variables d'environnement (decision
-- explicite du 2026-09-22, cf. SycaPayProperties) - AUCUNE ligne ici pour ces 3 champs.
-- Hibernate (ddl-auto=update) cree la table au premier demarrage - a appliquer manuellement une fois
-- apres ce premier demarrage, meme approche que les autres seeds manuels de ce dossier.
-- SYCAPAY_ENABLED=false par defaut ici (coherent avec le defaut de application.yml) : l'integration
-- reste desactivee tant qu'un administrateur ne l'active pas explicitement depuis le back-office ET
-- que les 3 credentials sont bien positionnes en environnement (cf. SycaPayProperties.validate() /
-- SycaPayPaymentService.hasCompleteCredentials()).

INSERT INTO t_assur_zen_dynamic_configuration
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
