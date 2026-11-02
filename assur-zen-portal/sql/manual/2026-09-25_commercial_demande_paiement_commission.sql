-- Demande de paiement de commission par le commercial interne (2026-09-25) - contrat
-- docs/api-contracts/commercial-demande-paiement-commission.md.
--
-- Les demandes des commerciaux sont stockees dans la table des demandes partenaire
-- (t_assur_zen_commission_partial_payment_request), pour que la validation admin existante
-- (POST /commissions/partial-payment-requests/{id}/valider) serve les deux.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement :
-- Hibernate (ddl-auto=update) cree seul les colonnes commercial_id et type, mais NE RELACHE JAMAIS la
-- contrainte NOT NULL existante de partenaire_id (meme approche que 2026-09-23_sycapay_partenaire_beneficiaire.sql).
-- Aucune donnee existante n'est modifiee.

-- 0) Controle prealable : les colonnes commercial_id et type doivent exister (creees par Hibernate).
--    Attendu : 2 lignes. Sinon, demarrer d'abord le gateway deploye.
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_commission_partial_payment_request'
  AND column_name IN ('commercial_id', 'type');

-- 1) Une demande de commercial n'a pas de partenaire.
ALTER TABLE t_assur_zen_commission_partial_payment_request
    MODIFY COLUMN partenaire_id BIGINT NULL;

-- 2) Verification : partenaire_id nullable = YES, et contrainte uk_cppr_commercial_idempotency_key
--    presente (creee par Hibernate au demarrage - constate en dev le 2026-09-25). Si elle manque
--    UNIQUEMENT, la creer avec :
--    ALTER TABLE t_assur_zen_commission_partial_payment_request
--        ADD CONSTRAINT uk_cppr_commercial_idempotency_key UNIQUE (commercial_id, idempotency_key);
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_commission_partial_payment_request'
  AND column_name IN ('partenaire_id', 'commercial_id', 'type');
SELECT constraint_name
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_commission_partial_payment_request'
  AND constraint_type = 'UNIQUE';
