-- Centre de notifications etendu au commercial interne et aux demandes de paiement (2026-10-03) -
-- contrat docs/api-contracts/espace-partenaire-ep2-2026-10-03.md (section 5).
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement :
-- Hibernate (ddl-auto=update) cree seul les colonnes user_id, objet_type, objet_id, supprimee et
-- supprimee_at, mais ne modifie JAMAIS une colonne existante : ni les valeurs de l'ENUM `type`, ni la
-- contrainte NOT NULL de partenaire_id. Tant que ce script n'est pas passe, les nouvelles notifications
-- (bonus, demande de paiement, commission commerciale) echouent a l'insertion : l'erreur est seulement
-- journalisee ("[NOTIFICATION] ... non creee"), le paiement ou la validation qui la declenche reste acquis.
-- Aucune donnee existante n'est modifiee.

-- 0) Controle prealable : les nouvelles colonnes doivent exister (creees par Hibernate). Attendu : 5 lignes.
--    Sinon, demarrer d'abord le gateway deploye.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_partenaire_notification'
  AND column_name IN ('user_id', 'objet_type', 'objet_id', 'supprimee', 'supprimee_at');

-- 1) Nouveaux types de notification (l'ordre des valeurs existantes est conserve).
ALTER TABLE t_assur_zen_partenaire_notification
    MODIFY COLUMN type ENUM('COMMISSION_DISPONIBLE', 'COMMISSION_GAGNEE', 'COMMISSION_VERSEE',
                            'DEMANDE_PAIEMENT_PAYEE', 'DEMANDE_PAIEMENT_ECHEC',
                            'BONUS_VALIDE', 'BONUS_PAYE', 'COMMISSION_COMMERCIALE_PAYEE') NOT NULL;

-- 2) Une notification de commercial n'a pas de partenaire (destinataire = user_id).
ALTER TABLE t_assur_zen_partenaire_notification
    MODIFY COLUMN partenaire_id BIGINT NULL;

-- 3) Verification. Attendu : type avec les 8 valeurs, partenaire_id is_nullable = YES.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_assur_zen_partenaire_notification'
  AND column_name IN ('type', 'partenaire_id');
