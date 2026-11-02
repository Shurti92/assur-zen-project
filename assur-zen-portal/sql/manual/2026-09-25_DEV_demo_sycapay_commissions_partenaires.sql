-- Donnees de demo SycaPay / commissions partenaires - BASE DEV LOCALE UNIQUEMENT (assur_zen_ci_db).
-- NE JAMAIS executer en recette ni en production.
--
-- Demande du frontend (2026-09-25) : transactions SycaPay de tous statuts (PARTENAIRE et COMMERCIAL),
-- paiements de commissions partenaires automatiques, un partenaire IMMEDIAT et des notifications.
--
-- Rejouable : la section 0 supprime d'abord les donnees de demo precedentes (references DEMO-*,
-- notifications suffixees "[demo]") et remet les contrats concernes en commission "pending".
-- Rollback : executer la section 0 seule, puis remettre le partenaire 7 a NULL (voir en fin de fichier).
--
-- Contrats utilises (signes, partenaire renseigne, commission non payee avant le script) :
--   9 (CTR2026000023, partenaire 3), 6 (CTR2026000018, partenaire 4), 12 (CTR2026000026, partenaire 5),
--   13 (CTR2026000027, partenaire 5), 49 (CTR2026000246, partenaire 3), 50 (CTR2026000249, partenaire 3).
-- Comme le backend reel : seul un virement SUCCESS cree un paiement (t_assur_zen_commission_payments) et
-- passe la commission du contrat en "paid". FAILED / UNKNOWN / PROCESSING / PENDING : commission "pending",
-- aucun paiement (FAILED reste payable manuellement ; les autres bloquent /commissions/pay).
-- Les lignes COMMERCIAL reprennent la convention des anciennes DEMO-* : commercial_id -900 et
-- contract_id negatifs (aucun commercial en base dev).
-- Les paiements automatiques NE passent PAS par le ledger (ledgerService.enregistrerCommission) :
-- les soldes du grand livre partenaire ne les refletent pas.

-- 0) Nettoyage des donnees de demo precedentes
DELETE FROM t_assur_zen_commission_payments WHERE reference LIKE 'DEMO-%';
DELETE FROM t_assur_zen_sycapay_transactions WHERE transaction_reference LIKE 'DEMO-%';
DELETE FROM t_assur_zen_partenaire_notification WHERE message LIKE '%[demo]';
UPDATE t_assur_zen_contracts SET commission_status = 'pending' WHERE id IN (6, 9, 12, 50);

-- 1) Transactions SycaPay (dates en UTC, sur 5 jours dont aujourd'hui)
INSERT INTO t_assur_zen_sycapay_transactions
    (created_at, created_by, deleted, updated_at, updated_by, version,
     transaction_reference, contract_id, commercial_id, partenaire_id, beneficiary_type,
     amount, currency, beneficiary_name, beneficiary_phone, status,
     sycapay_reference, sycapay_transaction_id, sycapay_response_code, sycapay_response_message,
     request_date, processing_date, completed_date, attempt_count, error_code, error_message, test_mode)
VALUES
    -- PARTENAIRE
    (UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 118 MINUTE, 'demo', 1,
     'DEMO-P-SUCCESS', 9, NULL, 3, 'PARTENAIRE',
     5000, 'XOF', 'Kouassi Serge', '0700000103', 'SUCCESS',
     'SYCDEMO000001', 'WAVEDEMO000001', 0, 'Virement Wave effectue',
     UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, UTC_TIMESTAMP(6) - INTERVAL 118 MINUTE, 1, NULL, NULL, 0),
    (UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'demo', 1,
     'DEMO-P-SUCCESS-2', 50, NULL, 3, 'PARTENAIRE',
     7000, 'XOF', 'Kouassi Serge', '0700000103', 'SUCCESS',
     'SYCDEMO000003', 'WAVEDEMO000003', 0, 'Virement Wave effectue',
     UTC_TIMESTAMP(6) - INTERVAL 3 DAY, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 1, NULL, NULL, 0),
    (UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'demo', 1,
     'DEMO-P-FAILED', 6, NULL, 4, 'PARTENAIRE',
     900, 'XOF', 'Bamba Aicha', '0700000104', 'FAILED',
     NULL, NULL, -1, 'Solde marchand insuffisant pour effectuer le virement',
     UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 1,
     'INSUFFICIENT_BALANCE', 'Solde Wave insuffisant pour ce virement', 0),
    (UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 29 MINUTE, 'demo', 1,
     'DEMO-P-UNKNOWN', 12, NULL, 5, 'PARTENAIRE',
     1500, 'XOF', 'Traore Ibrahim', '0700000105', 'UNKNOWN',
     NULL, NULL, NULL, NULL,
     UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE, NULL, 1,
     'TIMEOUT', 'Pas de reponse de SycaPay - reconciliation requise', 0),
    (UTC_TIMESTAMP(6) - INTERVAL 2 DAY, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 2 DAY, 'demo', 1,
     'DEMO-P-PROCESSING', 49, NULL, 3, 'PARTENAIRE',
     7000, 'XOF', 'Kouassi Serge', '0700000103', 'PROCESSING',
     NULL, NULL, NULL, 'Traitement en cours cote SycaPay',
     UTC_TIMESTAMP(6) - INTERVAL 2 DAY, UTC_TIMESTAMP(6) - INTERVAL 2 DAY, NULL, 1, NULL, NULL, 0),
    (UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE, 'demo', 0,
     'DEMO-P-PENDING', 13, NULL, 5, 'PARTENAIRE',
     2200, 'XOF', 'Traore Ibrahim', '0700000105', 'PENDING',
     NULL, NULL, NULL, NULL,
     UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE, NULL, NULL, 1, NULL, NULL, 0),
    -- COMMERCIAL
    (UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'demo', 1,
     'DEMO-C-SUCCESS', -911, -900, NULL, 'COMMERCIAL',
     25000, 'XOF', 'Demo Commercial', '0700000901', 'SUCCESS',
     'SYCDEMO000002', 'WAVEDEMO000002', 0, 'Virement Wave effectue',
     UTC_TIMESTAMP(6) - INTERVAL 3 DAY, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 1, NULL, NULL, 0),
    (UTC_TIMESTAMP(6) - INTERVAL 4 DAY, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 4 DAY, 'demo', 1,
     'DEMO-C-CANCELLED', -912, -900, NULL, 'COMMERCIAL',
     12000, 'XOF', 'Demo Commercial', '0700000901', 'CANCELLED',
     NULL, NULL, NULL, 'Virement annule par un administrateur',
     UTC_TIMESTAMP(6) - INTERVAL 4 DAY, NULL, UTC_TIMESTAMP(6) - INTERVAL 4 DAY, 1, NULL, NULL, 0),
    (UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, 'demo', 0, UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, 'demo', 1,
     'DEMO-C-FAILED', -913, -900, NULL, 'COMMERCIAL',
     30000, 'XOF', 'Demo Commercial', '0700000901', 'FAILED',
     NULL, NULL, -12, 'Numero Wave du beneficiaire invalide',
     UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, 2,
     'INVALID_BENEFICIARY', 'Numero Wave du beneficiaire invalide', 0);

-- 2) Paiements de commissions partenaires automatiques : uniquement les virements SUCCESS.
--    notification_envoyee : true (DEMO-P-SUCCESS), false (DEMO-P-SUCCESS-2, SMS en echec).
INSERT INTO t_assur_zen_commission_payments
    (contract_id, partenaire_id, amount, paid_by_id, automatic, sycapay_transaction_id, reference, paid_at, notification_envoyee)
SELECT t.contract_id, t.partenaire_id, c.partner_share, NULL, 1, t.id, t.transaction_reference,
       COALESCE(t.completed_date, t.request_date),
       CASE t.transaction_reference WHEN 'DEMO-P-SUCCESS' THEN 1 ELSE 0 END
FROM t_assur_zen_sycapay_transactions t
JOIN t_assur_zen_contracts c ON c.id = t.contract_id
WHERE t.transaction_reference IN ('DEMO-P-SUCCESS', 'DEMO-P-SUCCESS-2');

-- Contrats payes : ils ne sont plus proposes au paiement (evite un doublon sur uk_commission_payment_contract).
UPDATE t_assur_zen_contracts SET commission_status = 'paid' WHERE id IN (9, 50);

-- 3) Partenaire 7 (Kone Awa, aucun contrat) en frequence IMMEDIAT avec numero Wave
UPDATE t_assur_zen_partenaire
SET frequence_paiement_commission = 'IMMEDIAT', numero_wave_commission = '0757873487'
WHERE id = 7;

-- 4) Notifications partenaire (lues et non lues) pour les partenaires 3 et 4
INSERT INTO t_assur_zen_partenaire_notification
    (partenaire_id, type, titre, message, montant, contract_id, sms_envoye, lu, lu_at, created_at)
VALUES
    (3, 'COMMISSION_VERSEE', 'Commission versee',
     'Votre commission de 5 000 FCFA a ete versee sur votre compte Wave (ref. DEMO-P-SUCCESS). [demo]',
     5000, 9, 1, 0, NULL, UTC_TIMESTAMP(6) - INTERVAL 118 MINUTE),
    (3, 'COMMISSION_DISPONIBLE', 'Commissions disponibles',
     'Vous avez 7 000 FCFA de commissions disponibles. Vous pouvez en demander le paiement depuis votre espace partenaire. [demo]',
     7000, NULL, 1, 0, NULL, UTC_TIMESTAMP(6) - INTERVAL 1 DAY),
    (3, 'COMMISSION_GAGNEE', 'Nouvelle commission disponible',
     'Une nouvelle commission de 7 000 FCFA (contrat CTR2026000246) est disponible. Total disponible : 14 000 FCFA. [demo]',
     7000, 49, 1, 1, UTC_TIMESTAMP(6) - INTERVAL 2 DAY, UTC_TIMESTAMP(6) - INTERVAL 3 DAY),
    (4, 'COMMISSION_GAGNEE', 'Nouvelle commission disponible',
     'Une nouvelle commission de 900 FCFA (contrat CTR2026000018) est disponible. Total disponible : 900 FCFA. [demo]',
     900, 6, 0, 0, NULL, UTC_TIMESTAMP(6) - INTERVAL 4 HOUR),
    (4, 'COMMISSION_DISPONIBLE', 'Commissions disponibles',
     'Vous avez 900 FCFA de commissions disponibles. Vous pouvez en demander le paiement depuis votre espace partenaire. [demo]',
     900, NULL, 1, 1, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) - INTERVAL 2 DAY);

-- Rollback complet : section 0, puis
-- UPDATE t_assur_zen_partenaire SET frequence_paiement_commission = NULL, numero_wave_commission = NULL WHERE id = 7;
