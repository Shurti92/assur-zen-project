-- Contrats mobile (lecture + demandes de modification / resiliation) - 2026-11-02.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul la table t_assur_zen_contract_customer_request. Ce script ne fait que VERIFIER.
-- Aucune donnee existante n'est modifiee ; aucune cle de configuration obligatoire (MOBILE_RENEWAL_WINDOW_DAYS,
-- defaut 30 jours, et SMS_ALLOWED_NUMBERS / SMS_MOCK, variables d'environnement reservees au dev, sont optionnelles).

-- 0) Controle : la table existe. Attendu : 1 ligne.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_contract_customer_request';

-- 1) Unicite de la reference. Attendu : 1 ligne ; sinon creer la contrainte ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_contract_customer_request'
  AND index_name = 'uk_contract_customer_request_reference';
-- ALTER TABLE t_assur_zen_contract_customer_request ADD CONSTRAINT uk_contract_customer_request_reference UNIQUE (reference);

-- 2) Demandes des clients mobiles a traiter par l'equipe :
SELECT reference, contract_id, type, statut, created_at FROM t_assur_zen_contract_customer_request WHERE statut = 'EN_ATTENTE';
