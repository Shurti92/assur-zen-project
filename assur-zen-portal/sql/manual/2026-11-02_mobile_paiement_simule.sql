-- Paiement mobile SIMULE (demande mobile n°4) - 2026-11-02.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul la table t_assur_zen_mobile_payment. Ce script ne fait que VERIFIER.
--
-- IMPORTANT : en recette et en prod, laisser MOBILE_PAYMENTS_SIMULATED a false (valeur par defaut) : aucun moyen
-- de paiement n'est alors actif et toute initiation est refusee (METHOD_DISABLED). La simulation (aucun argent
-- encaisse, creation d'un contrat de renouvellement fictif) est reservee au dev.

-- 0) Controle : la table existe. Attendu : 1 ligne.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_mobile_payment';

-- 1) Unicite de la reference et de la cle d'idempotence par utilisateur. Attendu : 2 lignes ; sinon creer les contraintes ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_mobile_payment'
  AND index_name IN ('uk_mobile_payment_reference', 'uk_mobile_payment_idempotency');
-- ALTER TABLE t_assur_zen_mobile_payment ADD CONSTRAINT uk_mobile_payment_reference UNIQUE (reference);
-- ALTER TABLE t_assur_zen_mobile_payment ADD CONSTRAINT uk_mobile_payment_idempotency UNIQUE (user_id, idempotency_key);

-- 2) Controle de securite apres deploiement : aucune tentative ne doit exister en prod/recette.
SELECT COUNT(*) AS tentatives_simulees FROM t_assur_zen_mobile_payment;
