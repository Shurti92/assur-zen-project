-- Date de signature des contrats signes qui n'en ont pas (2026-10-04).
--
-- Jusqu'au correctif du 2026-10-04 (ContractsService.signedAtEffectif), signed_at n'etait rempli que
-- s'il etait envoye a la creation, et une modification sans signed_at l'effacait. Sur la copie de la
-- prod du 2026-10-04 : 360 contrats signes sur 449 sans signed_at (5 000 755 FCFA de primes), dont 356
-- ventes directes et 4 contrats de partenaires. Ces contrats sont ignores par :
--   - la performance / le bonus des commerciaux (CommercialProductionService) : AUCUN n'a ete vendu par
--     un commercial interne sur la copie -> aucun bonus ne change ;
--   - le taux de realisation de l'objectif partenaire (PartenaireService.computeObjectifRealise) et donc
--     la prime d'objectif : partenaires 1 (24 % -> 32 %), 5 (0 -> 16 %), 8 (0 -> 13,5 %) sur la copie,
--     tous sous le seuil de prime (PRIME_OBJECTIF_SEUIL_BASE = 100 %) -> aucune prime ne change.
-- Les controles 0) le reverifient sur la base cible AVANT toute modification.
--
-- Date retenue : la date de creation du contrat (meme repli que le tableau de bord). Aucun montant
-- n'est modifie. A appliquer UNE fois, APRES le deploiement du WAR qui contient le correctif (sinon de
-- nouveaux contrats sans date peuvent encore apparaitre). Rejouable sans effet (ne touche que signed_at NULL).

-- 0a) Contrats concernes. Attendu sur la copie de la prod du 2026-10-04 : 360 / 5 000 755.
SELECT COUNT(*) AS contrats, SUM(premium) AS primes
FROM t_assur_zen_contracts
WHERE status = 'signed' AND signed_at IS NULL;

-- 0b) Contrats concernes vendus par un commercial interne (bonus). Attendu : 0.
--     Si > 0 : NE PAS CONTINUER sans l'accord de l'utilisateur (le bonus de ces commerciaux changerait
--     pour les mois non encore valides).
SELECT c.id, c.policy_number, c.sales_rep_id, DATE(c.created_at) AS date_retenue, c.premium
FROM t_assur_zen_contracts c
JOIN t_assur_zen_profiles p ON p.user_id = c.sales_rep_id
JOIN t_assur_zen_commercial cm ON cm.profile_id = p.id
WHERE c.status = 'signed' AND c.signed_at IS NULL;

-- 0c) Effet sur l'objectif des partenaires (exercice 2026). Attendu : realise_apres / objectif < 100 %
--     pour chaque ligne. Sinon : NE PAS CONTINUER sans l'accord de l'utilisateur (prime d'objectif).
SELECT c.partenaire_id, pa.nom, pa.objectif_production,
       SUM(CASE WHEN c.signed_at BETWEEN '2026-01-01' AND '2026-12-31' THEN c.premium ELSE 0 END) AS realise_actuel,
       SUM(CASE WHEN c.signed_at BETWEEN '2026-01-01' AND '2026-12-31'
                  OR (c.signed_at IS NULL AND DATE(c.created_at) BETWEEN '2026-01-01' AND '2026-12-31')
                THEN c.premium ELSE 0 END) AS realise_apres
FROM t_assur_zen_contracts c
JOIN t_assur_zen_partenaire pa ON pa.id = c.partenaire_id
WHERE c.status = 'signed'
  AND c.partenaire_id IN (SELECT partenaire_id FROM t_assur_zen_contracts
                          WHERE status = 'signed' AND signed_at IS NULL AND partenaire_id IS NOT NULL)
GROUP BY c.partenaire_id, pa.nom, pa.objectif_production;

-- 1) Sauvegarde des lignes modifiees (retour arriere : UPDATE ... SET signed_at = NULL WHERE id IN (SELECT id FROM la sauvegarde)).
CREATE TABLE t_assur_zen_contracts_backup_signed_at_20261004 AS
SELECT id, policy_number, signed_at, created_at
FROM t_assur_zen_contracts
WHERE status = 'signed' AND signed_at IS NULL;

-- 2) Correction.
UPDATE t_assur_zen_contracts
SET signed_at = DATE(created_at)
WHERE status = 'signed' AND signed_at IS NULL;

-- 3) Verification. Attendu : 0.
SELECT COUNT(*) AS contrats_signes_sans_date
FROM t_assur_zen_contracts
WHERE status = 'signed' AND signed_at IS NULL;
