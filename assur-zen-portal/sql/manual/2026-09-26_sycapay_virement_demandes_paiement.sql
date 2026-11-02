-- Virement SycaPay a la validation des demandes de paiement (avances partenaire, demandes des commerciaux)
-- et imputation des avances - 2026-09-26.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul les nouvelles colonnes (partial_payment_request_id, montant_impute,
-- avance_imputee) et la contrainte d'unicite, mais NE MODIFIE JAMAIS un type ENUM MySQL existant ni une
-- contrainte NOT NULL existante. Aucune donnee existante n'est modifiee.

-- 0) Controle prealable : les nouvelles colonnes doivent exister (creees par Hibernate). Attendu : 3 lignes.
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_assur_zen_sycapay_transactions' AND column_name = 'partial_payment_request_id')
    OR (table_name = 't_assur_zen_commission_partial_payment_request' AND column_name = 'montant_impute')
    OR (table_name = 't_assur_zen_commission_payments' AND column_name = 'avance_imputee'));

-- 1) Nouveaux statuts des demandes de paiement : virement en cours / en echec.
ALTER TABLE t_assur_zen_commission_partial_payment_request
    MODIFY COLUMN status ENUM('pending', 'paid', 'processing', 'failed') NOT NULL;

-- 2) Un virement de demande de paiement n'est rattache a aucun contrat.
ALTER TABLE t_assur_zen_sycapay_transactions
    MODIFY COLUMN contract_id BIGINT NULL;

-- 3) Verification : statut avec les 4 valeurs, contract_id nullable = YES.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_assur_zen_commission_partial_payment_request' AND column_name = 'status')
    OR (table_name = 't_assur_zen_sycapay_transactions' AND column_name = 'contract_id'));
