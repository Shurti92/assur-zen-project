-- Support, FAQ, avis, notifications in-app et profil mobile - 2026-11-03.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul les quatre tables ci-dessous. Ce script ne fait que VERIFIER. Aucune donnee existante
-- n'est modifiee. Les cles de configuration (SUPPORT_TOPICS, LEGAL_*, NOTIFICATION_RETENTION_MONTHS, delais annonces)
-- sont creees automatiquement au demarrage, vides sauf SUPPORT_TOPICS (4 sujets) et la conservation (12 mois).

-- 0) Controle : les tables existent. Attendu : 4 lignes.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_assur_zen_mobile_notification', 't_assur_zen_mobile_feedback', 't_assur_zen_support_request', 't_assur_zen_mobile_faq');

-- 1) Unicite : reference des demandes d'aide et cle anti-doublon des notifications. Attendu : 2 lignes ;
--    sinon creer les contraintes ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND index_name IN ('uk_support_request_reference', 'uk_mobile_notification_dedupe');
-- ALTER TABLE t_assur_zen_support_request ADD CONSTRAINT uk_support_request_reference UNIQUE (reference);
-- ALTER TABLE t_assur_zen_mobile_notification ADD CONSTRAINT uk_mobile_notification_dedupe UNIQUE (user_id, dedupe_key);

-- 2) File de travail de l'equipe : demandes d'aide en attente.
SELECT reference, topic, statut, created_at FROM t_assur_zen_support_request WHERE statut IN ('EN_ATTENTE', 'EN_COURS') ORDER BY created_at;

-- 3) Cles de configuration creees au demarrage. Attendu : 14 lignes environ.
SELECT config_key, config_value FROM t_assur_zen_dynamic_configuration
WHERE config_key IN ('SUPPORT_TOPICS', 'LEGAL_TERMS_URL', 'LEGAL_PRIVACY_URL', 'LEGAL_UPDATED_AT', 'NOTIFICATION_RETENTION_MONTHS',
                     'CALLBACK_EXPECTED_MINUTES', 'SUPPORT_RESPONSE_MINUTES', 'CLAIM_HANDLING_HOURS', 'CONTRACT_REQUEST_RESPONSE_HOURS',
                     'SUPPORT_TELEPHONE_2', 'SUPPORT_ADRESSE', 'SUPPORT_DISPONIBILITE', 'SUPPORT_URGENCES')
ORDER BY config_key;
