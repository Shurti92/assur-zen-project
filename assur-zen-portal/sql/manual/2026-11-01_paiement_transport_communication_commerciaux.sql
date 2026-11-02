-- Paiement separe de la prime de transport et de l'indemnite de communication des commerciaux - 2026-11-01.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul la table t_assur_zen_commercial_benefit_payment (avec sa contrainte
-- d'unicite commercial/mois/type) et la colonne t_assur_zen_sycapay_transactions.commercial_benefit_payment_id,
-- et les comptes comptables 661400 / 661500 sont seedes au demarrage (LedgerService.seedPlanComptable).
-- Ce script ne fait que VERIFIER et poser la contrainte d'unicite de la transaction SycaPay si Hibernate
-- ne l'a pas creee. Aucune donnee existante n'est modifiee ; aucun type ENUM n'est modifie.

-- 0) Controle : la table et la colonne existent. Attendu : 2 lignes.
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_assur_zen_commercial_benefit_payment' AND column_name = 'type')
    OR (table_name = 't_assur_zen_sycapay_transactions' AND column_name = 'commercial_benefit_payment_id'));

-- 1) Une seule transaction SycaPay par avantage (garde d'idempotence ultime contre un double virement).
--    A executer UNIQUEMENT si la requete suivante ne renvoie aucune ligne.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_sycapay_transactions'
  AND index_name = 'uk_sycapay_transaction_benefit_payment';

ALTER TABLE t_assur_zen_sycapay_transactions
    ADD CONSTRAINT uk_sycapay_transaction_benefit_payment UNIQUE (commercial_benefit_payment_id);

-- 2) Une seule ligne par commercial, mois et type (normalement deja creee par Hibernate : verifier).
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_commercial_benefit_payment'
  AND index_name = 'uk_commercial_benefit_payment';

-- 3) Verification : comptes de charge presents. Attendu : 2 lignes.
SELECT code, libelle, type FROM t_assur_zen_compte_comptable WHERE code IN ('661400', '661500');
