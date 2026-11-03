-- Catalogue public, demande de rappel et administration des comptes mobile - 2026-11-03.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul la table t_assur_zen_callback_request et ajoute les colonnes de traitement
-- (processed_by, process_comment) aux tables des demandes. Ce script ne fait que VERIFIER. Aucune donnee
-- existante n'est modifiee.

-- 0) Controle : la table des rappels existe. Attendu : 1 ligne.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_callback_request';

-- 1) Colonnes de traitement ajoutees. Attendu : 6 lignes (3 tables x 2 colonnes).
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('t_assur_zen_callback_request', 't_assur_zen_contract_customer_request', 't_assur_zen_account_deletion_request')
  AND column_name IN ('processed_by', 'process_comment');

-- 2) Unicite de la reference des rappels. Attendu : 1 ligne ; sinon creer la contrainte ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_callback_request' AND index_name = 'uk_callback_request_reference';
-- ALTER TABLE t_assur_zen_callback_request ADD CONSTRAINT uk_callback_request_reference UNIQUE (reference);

-- 3) Cles de configuration optionnelles : CALLBACK_EXPECTED_MINUTES (delai annonce au client pour un rappel ; absent = aucun
--    engagement affiche), MOBILE_RENEWAL_WINDOW_DAYS (defaut 30), MOBILE_MAX_OPEN_CLAIMS_PER_CONTRACT (defaut 5).

-- 4) File de travail de l'equipe :
SELECT reference, phone, slot, statut, created_at FROM t_assur_zen_callback_request WHERE statut = 'EN_ATTENTE' ORDER BY created_at;
