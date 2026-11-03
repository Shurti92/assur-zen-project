-- Authentification et inscription mobile, lot 1 - 2026-11-02.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul les deux nouvelles tables. Ce script ne fait que VERIFIER et poser les
-- contraintes d'unicite si Hibernate ne les a pas creees. Aucune donnee existante n'est modifiee ; le role
-- CLIENT est une simple valeur de t_assur_zen_user.roles (aucun type ENUM a modifier).

-- 0) Controle : les deux tables existent. Attendu : 2 lignes.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_assur_zen_client_account_insured', 't_assur_zen_account_deletion_request');

-- 1) Unicite compte/assure et reference de demande de suppression. Attendu : 2 lignes
--    (uk_client_account_insured, uk_account_deletion_reference). Si une ligne manque, creer la contrainte ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('uk_client_account_insured', 'uk_account_deletion_reference');

-- ALTER TABLE t_assur_zen_client_account_insured ADD CONSTRAINT uk_client_account_insured UNIQUE (user_id, insured_id);
-- ALTER TABLE t_assur_zen_account_deletion_request ADD CONSTRAINT uk_account_deletion_reference UNIQUE (reference);

-- 2) Quota de codes SMS (3 par heure et par numero) : cle optionnelle de configuration dynamique
--    OTP_MAX_REQUESTS_PER_HOUR (defaut 3 si absente) - rien a inserer.

-- 3) Les demandes de suppression de compte a traiter par un administrateur :
SELECT reference, user_id, statut, requested_at FROM t_assur_zen_account_deletion_request WHERE statut = 'EN_ATTENTE';
