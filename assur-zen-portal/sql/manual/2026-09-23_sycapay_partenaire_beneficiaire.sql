-- Paiement des commissions PARTENAIRE selon la frequence (2026-09-23).
-- A appliquer manuellement UNE fois, APRES le premier demarrage du gateway qui deploie ce changement :
-- Hibernate (ddl-auto=update) cree seul les nouvelles colonnes nullables (beneficiary_type,
-- partenaire_id, automatic, sycapay_transaction_id), la nouvelle table t_assur_zen_partenaire_notification
-- et les nouvelles valeurs d'enum, mais NE RELACHE JAMAIS une contrainte NOT NULL existante.
-- Meme approche que 2026-09-13_vehicule_immatriculation_nullable.sql.

-- 1) Une transaction SycaPay partenaire n'a pas de commercial.
ALTER TABLE t_assur_zen_sycapay_transactions
    MODIFY COLUMN commercial_id BIGINT NULL;

-- 2) Toutes les transactions anterieures sont des commissions commerciales (le code traite deja NULL
--    comme COMMERCIAL, ce backfill rend simplement la donnee explicite).
UPDATE t_assur_zen_sycapay_transactions
SET beneficiary_type = 'COMMERCIAL'
WHERE beneficiary_type IS NULL;

-- 3) Un paiement de commission automatique (SycaPay) n'a pas d'admin a l'origine.
ALTER TABLE t_assur_zen_commission_payments
    MODIFY COLUMN paid_by_id BIGINT NULL;

-- 4) Modeles SMS (en prod, spring.sql.init.mode=never : data.sql n'est pas rejoue). Echoue sans effet
--    si le modele existe deja (cle unique uk_template_code).
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_DISPONIBLE', 'Commissions disponibles (rappel periodique)',
     'SMS de rappel envoye aux partenaires HEBDOMADAIRE (lundi), QUINZAINE (1er et 16) et MENSUELLE (1er) a 9h s''il leur reste des commissions a payer (PartenaireCommissionAutomationService.envoyerRappels).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : vous avez {{montant}} FCFA de commissions disponibles. Vous pouvez en demander le paiement depuis votre espace partenaire.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_GAGNEE', 'Nouvelle commission gagnee (partenaire A LA DEMANDE)',
     'SMS envoye a un partenaire A_LA_DEMANDE a chaque contrat signe (nouvelle commission gagnee).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : nouvelle commission de {{montant}} FCFA (contrat {{police}}). Total disponible : {{total}} FCFA. Demandez-en le paiement depuis votre espace partenaire.',
     'ACTIF', true, NOW(), NOW(), false, 0);
