-- Nouvelles notifications de l'Espace Partenaire (2026-10-04) : echeances, primes a reverser, documents,
-- securite - contrat docs/api-contracts/espace-partenaire-ep2-2026-10-03.md (sections 7 a 9).
--
-- A appliquer UNE fois, APRES 2026-10-03_notifications_commercial.sql et apres le premier demarrage du
-- gateway qui deploie ce changement. Hibernate (ddl-auto=update) cree seul les nouvelles colonnes des
-- documents (statut_validation, motif_refus, valide_le, valide_par_id) et des sessions (rotated_at), et la
-- table t_assur_zen_notification_preference, mais ne modifie jamais les valeurs d'un ENUM existant.
-- Tant que ce script n'est pas passe, ces nouvelles notifications echouent a l'insertion (erreur seulement
-- journalisee, l'operation qui les declenche - connexion, refus de document - reste acquise).
-- Aucune donnee existante n'est modifiee.
--
-- Rappel : les rappels planifies (echeances, primes, documents) restent DESACTIVES tant que
-- NOTIFICATIONS_RAPPELS_AUTO_ENABLED n'est pas mis a true dans le back-office (module Configuration).

-- 0) Controle prealable : 8 valeurs attendues (script du 2026-10-03 deja passe).
SELECT column_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_partenaire_notification' AND column_name = 'type';

-- 1) Nouveaux types (ordre des valeurs existantes conserve).
ALTER TABLE t_assur_zen_partenaire_notification
    MODIFY COLUMN type ENUM('COMMISSION_DISPONIBLE', 'COMMISSION_GAGNEE', 'COMMISSION_VERSEE',
                            'DEMANDE_PAIEMENT_PAYEE', 'DEMANDE_PAIEMENT_ECHEC',
                            'BONUS_VALIDE', 'BONUS_PAYE', 'COMMISSION_COMMERCIALE_PAYEE',
                            'ECHEANCE_PROCHE', 'PRIME_NON_REVERSEE', 'DOCUMENT_EXPIRE_BIENTOT',
                            'DOCUMENT_EXPIRE', 'DOCUMENT_REFUSE', 'NOUVELLE_CONNEXION') NOT NULL;

-- 2) Verification : 14 valeurs.
SELECT column_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_partenaire_notification' AND column_name = 'type';
