-- Script manuel de secours si hibernate.ddl-auto=update ne convertit pas
-- correctement les colonnes existantes de TIMESTAMP/DATETIME vers DATE.
-- A executer uniquement si necessaire, apres verification en base.
-- N'est PAS execute automatiquement par l'application (contrairement a data.sql).

-- 1. Sauvegarde de securite avant modification
CREATE TABLE assur_zen_ci_db.t_assur_zen_contracts_backup_20260728
    AS SELECT * FROM assur_zen_ci_db.t_assur_zen_contracts;

-- 2. Conversion des colonnes (la partie heure est tronquee)
ALTER TABLE assur_zen_ci_db.t_assur_zen_contracts
    MODIFY COLUMN effective_date DATE,
    MODIFY COLUMN signed_at DATE;

-- 3. Verification
-- SELECT COLUMN_NAME, DATA_TYPE
-- FROM information_schema.columns
-- WHERE table_schema = 'assur_zen_ci_db'
--   AND table_name = 't_assur_zen_contracts'
--   AND column_name IN ('effective_date', 'signed_at');

-- 4. Si tout est correct, la table de sauvegarde peut etre supprimee :
-- DROP TABLE assur_zen_ci_db.t_assur_zen_contracts_backup_20260728;
