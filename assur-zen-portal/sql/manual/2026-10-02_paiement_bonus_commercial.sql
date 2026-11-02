-- Paiement reel du bonus mensuel des commerciaux (virement SycaPay) - 2026-10-02.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul les nouvelles colonnes (nullable) et le compte comptable 661300 est seede
-- au demarrage (LedgerService.seedPlanComptable). Ce script ne fait que VERIFIER, et pose la contrainte
-- d'unicite si Hibernate ne l'a pas creee. Aucune donnee existante n'est modifiee ; aucun type ENUM
-- n'est modifie (l'ecriture comptable du bonus reutilise le type PRIME_OBJECTIF deja present).

-- 0) Controle prealable : les nouvelles colonnes doivent exister (creees par Hibernate). Attendu : 4 lignes.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_assur_zen_sycapay_transactions' AND column_name = 'commercial_performance_id')
    OR (table_name = 't_assur_zen_commercial_performance'
        AND column_name IN ('sycapay_transaction_id', 'mode_paiement', 'payment_reference')));

-- 1) Une seule transaction SycaPay par bonus (garde d'idempotence ultime contre un double virement).
--    A executer UNIQUEMENT si la requete suivante ne renvoie aucune ligne.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_sycapay_transactions'
  AND index_name = 'uk_sycapay_transaction_commercial_perf';

ALTER TABLE t_assur_zen_sycapay_transactions
    ADD CONSTRAINT uk_sycapay_transaction_commercial_perf UNIQUE (commercial_performance_id);

-- 2) Verification : compte de charge des bonus commerciaux present.
SELECT code, libelle, type FROM t_assur_zen_compte_comptable WHERE code = '661300';
