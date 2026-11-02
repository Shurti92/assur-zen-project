-- =====================================================================================
-- AUDIT PRODUCTION (LECTURE SEULE) - commission partenaire (partner_share) derivee - 2026-09-25
--
-- Contexte : jusqu'au commit c0e3013, le backend ecrasait la commission partenaire saisie par
-- premium - commission_amount (regle introduite le 2026-08-21). Cette commission (partner_share)
-- est le montant verse au partenaire : paiement manuel (t_assur_zen_commission_payments), solde
-- des demandes de paiement partiel et, une fois SYCAPAY_ENABLED active, virement Wave automatique.
--
-- Critere "commission derivee" : contrat AVEC partenaire et partner_share = premium - commission_amount.
-- C'est une presomption, pas une preuve : une saisie peut coincider avec ce calcul par hasard.
--
-- AUCUNE ecriture : uniquement des SELECT. Lancer section par section et m'envoyer les resultats.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) VUE D'ENSEMBLE - par mois de creation : combien de contrats partenaires ont une commission
--    derivee. Attendu : ~0 avant la mise en production de la regle du 21/08, massif ensuite.
--    Permet de dater precisement le debut du probleme en production.
-- -------------------------------------------------------------------------------------
SELECT DATE_FORMAT(c.created_at, '%Y-%m')                                   AS mois_creation,
       COUNT(*)                                                             AS contrats_partenaire,
       SUM(c.partner_share = c.premium - c.commission_amount)               AS commission_derivee,
       SUM(c.partner_share = c.premium - c.commission_amount
           AND c.status = 'signed' AND c.commission_status = 'pending')     AS derivee_signee_a_payer
FROM t_assur_zen_contracts c
WHERE c.partenaire_id IS NOT NULL
GROUP BY DATE_FORMAT(c.created_at, '%Y-%m')
ORDER BY mois_creation;

-- -------------------------------------------------------------------------------------
-- 2) PAR PARTENAIRE - ce qui serait verse aujourd'hui (contrats signes, commission en attente).
--    montant_derive_a_payer = somme des commissions presumees fausses.
--    Les partenaires en IMMEDIAT (7 et 8) seraient payes automatiquement des l'activation SycaPay.
-- -------------------------------------------------------------------------------------
SELECT p.id                                   AS partenaire_id,
       p.code_partenaire,
       CONCAT(p.nom, ' ', COALESCE(p.prenoms, '')) AS partenaire,
       p.type                                 AS type_partenaire,
       p.frequence_paiement_commission        AS frequence,
       COUNT(*)                               AS contrats_signes_a_payer,
       SUM(c.partner_share)                   AS total_a_payer,
       SUM(c.partner_share = c.premium - c.commission_amount)                         AS contrats_derives,
       SUM(IF(c.partner_share = c.premium - c.commission_amount, c.partner_share, 0)) AS montant_derive_a_payer
FROM t_assur_zen_contracts c
JOIN t_assur_zen_partenaire p ON p.id = c.partenaire_id
WHERE c.status = 'signed'
  AND c.commission_status = 'pending'
GROUP BY p.id, p.code_partenaire, p.nom, p.prenoms, p.type, p.frequence_paiement_commission
ORDER BY montant_derive_a_payer DESC;

-- -------------------------------------------------------------------------------------
-- 3) DETAIL - contrats a commission derivee, du plus recent au plus ancien, avec la part de
--    commission partenaire par rapport a la prime (une "commission" a 80-90 % de la prime est un
--    signal quasi certain d'erreur). C'est la liste a faire corriger (saisie de la vraie valeur).
-- -------------------------------------------------------------------------------------
SELECT c.id,
       c.policy_number,
       c.created_at,
       c.status,
       c.commission_status,
       p.id                                          AS partenaire_id,
       CONCAT(p.nom, ' ', COALESCE(p.prenoms, ''))   AS partenaire,
       p.frequence_paiement_commission               AS frequence,
       pr.nom_produit                                AS produit,
       c.premium,
       c.commission_amount                           AS commission_assurzen,
       c.partner_share                               AS commission_partenaire_enregistree,
       ROUND(100 * c.partner_share / NULLIF(c.premium, 0), 1) AS pct_de_la_prime
FROM t_assur_zen_contracts c
JOIN t_assur_zen_partenaire p ON p.id = c.partenaire_id
LEFT JOIN t_assur_zen_produit pr ON pr.id = c.product_id
WHERE c.partner_share = c.premium - c.commission_amount
ORDER BY c.created_at DESC;

-- -------------------------------------------------------------------------------------
-- 4) DEJA PAYES - paiements de commission enregistres sur des contrats a commission derivee :
--    montants potentiellement verses en trop (a verifier au cas par cas avec le partenaire).
-- -------------------------------------------------------------------------------------
SELECT cp.id                 AS paiement_id,
       cp.paid_at,
       cp.automatic,
       cp.reference,
       cp.amount             AS montant_paye,
       c.id                  AS contract_id,
       c.policy_number,
       c.premium,
       c.commission_amount   AS commission_assurzen,
       p.id                  AS partenaire_id,
       CONCAT(p.nom, ' ', COALESCE(p.prenoms, '')) AS partenaire
FROM t_assur_zen_commission_payments cp
JOIN t_assur_zen_contracts c ON c.id = cp.contract_id
LEFT JOIN t_assur_zen_partenaire p ON p.id = cp.partenaire_id
WHERE c.partner_share = c.premium - c.commission_amount
ORDER BY cp.paid_at DESC;

-- 4.b Total deja paye sur ces contrats.
SELECT COUNT(*) AS paiements, COALESCE(SUM(cp.amount), 0) AS total_paye
FROM t_assur_zen_commission_payments cp
JOIN t_assur_zen_contracts c ON c.id = cp.contract_id
WHERE c.partner_share = c.premium - c.commission_amount;

-- -------------------------------------------------------------------------------------
-- 5) DEMANDES DE PAIEMENT PARTIEL en cours : leur solde est calcule sur partner_share, donc
--    potentiellement gonfle. A examiner avant toute validation.
-- -------------------------------------------------------------------------------------
SELECT r.id, r.partenaire_id, r.status, r.requested_amount, r.pending_balance_before, r.requested_at
FROM t_assur_zen_commission_partial_payment_request r
WHERE r.status = 'pending'
ORDER BY r.requested_at DESC;

-- -------------------------------------------------------------------------------------
-- 6) SYCAPAY - aucun virement partenaire ne doit exister (SYCAPAY_ENABLED=false en prod).
--    Toute ligne ici sur un contrat a commission derivee est a signaler immediatement.
-- -------------------------------------------------------------------------------------
SELECT t.id, t.transaction_reference, t.status, t.test_mode, t.amount, t.request_date,
       t.contract_id, t.partenaire_id, t.beneficiary_type
FROM t_assur_zen_sycapay_transactions t
JOIN t_assur_zen_contracts c ON c.id = t.contract_id
WHERE c.partner_share = c.premium - c.commission_amount
ORDER BY t.request_date DESC;
