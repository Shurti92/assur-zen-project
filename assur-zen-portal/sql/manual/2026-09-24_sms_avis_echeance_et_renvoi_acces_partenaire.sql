-- =====================================================================================
-- LOCAL + PRODUCTION - 2026-09-24
--   A) Rappels SMS d'avis d'echeance DEJA PLANIFIES (statut en_attente) : nouveau texte
--      (numero sans prefixe CTR + numeros de contact), identique a celui que genere desormais
--      SmsNotificationService.scheduleContractReminders().
--   B) Modele RENVOI_ACCES_PARTENAIRE present et ACTIF (UserProfileService.resendAccess).
--
-- ORDRE : lancer la section 0 SEULE, verifier les chiffres, puis 1, 2, 3, 4 dans l'ordre.
-- Seuls les avis_echeance en_attente sont touches : envoye / non_envoye / message_anti_date et les
-- autres types (fin_contrat, relance_echeance_paiement...) ne changent pas.
-- REJOUABLE : la sauvegarde est en CREATE TABLE IF NOT EXISTS, et l'UPDATE ne reprend pas les
-- lignes deja au nouveau texte.
-- Aucun secret dans ce fichier.
-- =====================================================================================

-- La signature contient des accents (serenite) : forcer l'UTF-8 de la connexion, sinon ils
-- arrivent en "?" (deja constate sur un seed avec emojis le 2026-09-15).
SET NAMES utf8mb4;

-- -------------------------------------------------------------------------------------
-- 0) PRE-CONTROLE (lecture seule) - a lancer SEUL d'abord.
-- -------------------------------------------------------------------------------------
-- 0.a Nombre de rappels concernes (= nombre de lignes que l'UPDATE de la section 2 modifiera,
--     hors lignes deja a jour) et combien sont exploitables (date lisible dans le message).
SELECT
    COUNT(*)                                                                    AS avis_en_attente,
    SUM(n.contract_id IS NULL)                                                  AS sans_contrat_ignores,
    SUM(STR_TO_DATE(SUBSTRING(n.message, LOCATE(' le ', n.message) + 4, 10), '%Y-%m-%d') IS NULL)
                                                                                AS date_illisible_ignores,
    SUM(n.message LIKE '%0705206698%')                                          AS deja_au_nouveau_texte
FROM t_assur_zen_notificateur_sms n
WHERE n.type_message = 'avis_echeance'
  AND n.statut_envoi = 'en_attente';

-- 0.b Apercu : ancien texte -> nouveau texte (20 premieres lignes).
SELECT n.id, n.date_envoi, n.message AS ancien_texte,
       CONCAT('Cher(e) assure, votre contrat n ',
              IF(c.policy_number LIKE 'CTR%', SUBSTRING(c.policy_number, 4), c.policy_number),
              ' arrivera a echeance le ',
              SUBSTRING(n.message, LOCATE(' le ', n.message) + 4, 10),
              '. Contactez-nous au 0705206698 ou 0747304386 (whatsapp) pour le renouveler. AssurZen Assurance Votre sérénité, Notre engagement'
       ) AS nouveau_texte
FROM t_assur_zen_notificateur_sms n
JOIN t_assur_zen_contracts c ON c.id = n.contract_id
WHERE n.type_message = 'avis_echeance'
  AND n.statut_envoi = 'en_attente'
ORDER BY n.date_envoi
LIMIT 20;

-- 0.c Etat actuel du modele de renvoi d'acces partenaire (0 ligne = absent).
SELECT id, code, statut, active, deleted, contenu
FROM t_assur_zen_communication_template
WHERE code = 'RENVOI_ACCES_PARTENAIRE';

-- -------------------------------------------------------------------------------------
-- 1) SAUVEGARDE des rappels qui vont etre modifies (table a supprimer une fois valide).
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bak_20260924_notificateur_sms_avis_echeance AS
SELECT n.*
FROM t_assur_zen_notificateur_sms n
WHERE n.type_message = 'avis_echeance'
  AND n.statut_envoi = 'en_attente';

SELECT COUNT(*) AS lignes_sauvegardees FROM bak_20260924_notificateur_sms_avis_echeance;

-- -------------------------------------------------------------------------------------
-- 2) MISE A JOUR des rappels en_attente. Le numero vient du contrat et la date reste celle deja
--    ecrite dans le message (date d'echeance connue au moment de la planification, coherente
--    avec date_envoi). Le nombre de lignes modifiees doit egaler 0.a avis_en_attente moins les
--    colonnes "ignores" et "deja_au_nouveau_texte".
-- -------------------------------------------------------------------------------------
UPDATE t_assur_zen_notificateur_sms n
JOIN t_assur_zen_contracts c ON c.id = n.contract_id
SET n.message = CONCAT('Cher(e) assure, votre contrat n ',
                       IF(c.policy_number LIKE 'CTR%', SUBSTRING(c.policy_number, 4), c.policy_number),
                       ' arrivera a echeance le ',
                       SUBSTRING(n.message, LOCATE(' le ', n.message) + 4, 10),
                       '. Contactez-nous au 0705206698 ou 0747304386 (whatsapp) pour le renouveler. AssurZen Assurance Votre sérénité, Notre engagement')
WHERE n.type_message = 'avis_echeance'
  AND n.statut_envoi = 'en_attente'
  AND n.message NOT LIKE '%0705206698%'
  AND STR_TO_DATE(SUBSTRING(n.message, LOCATE(' le ', n.message) + 4, 10), '%Y-%m-%d') IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- 3) MODELE RENVOI_ACCES_PARTENAIRE : creation s'il manque (contenu identique a data.sql), puis
--    activation s'il existe en BROUILLON / inactif / supprime. Le contenu existant n'est pas
--    ecrase s'il a ete personnalise.
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'RENVOI_ACCES_PARTENAIRE', 'Renvoi des acces (partenaire)',
     'SMS envoye a la demande d''un administrateur pour renvoyer les acces d''un compte PARTENAIRE deja existant (UserProfileService.resendAccess) - {{lienApplication}} vient de app.partenaire.lien-connexion (prod vs sandbox).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Vos acces AssurZen ont ete renvoyes. Connectez-vous sur {{lienApplication}} avec l''identifiant : {{username}} et le nouveau mot de passe temporaire : {{motDePasse}}. Changez-le des que possible.',
     'ACTIF', true, NOW(), NOW(), false, 0);

UPDATE t_assur_zen_communication_template
SET statut = 'ACTIF', active = true, deleted = false, updated_at = NOW()
WHERE code = 'RENVOI_ACCES_PARTENAIRE'
  AND (statut <> 'ACTIF' OR active = false OR deleted = true);

-- -------------------------------------------------------------------------------------
-- 4) POST-CONTROLE (lecture seule).
-- -------------------------------------------------------------------------------------
-- 4.a restant_ancien_texte doit valoir 0 (sauf lignes "ignores" du 0.a) ; accents_ok doit
--     egaler nouveau_texte (sinon la signature est arrivee en "?" : restaurer depuis la
--     sauvegarde et relancer avec un client en UTF-8).
SELECT
    SUM(n.message LIKE '%0705206698%')              AS nouveau_texte,
    SUM(n.message NOT LIKE '%0705206698%')          AS restant_ancien_texte,
    SUM(n.message LIKE '%Votre sérénité, Notre engagement') AS accents_ok
FROM t_assur_zen_notificateur_sms n
WHERE n.type_message = 'avis_echeance'
  AND n.statut_envoi = 'en_attente';

-- 4.b Le modele doit sortir en ACTIF / active=1 / deleted=0, avec categorie et langue renseignees.
SELECT code, statut, active, deleted, categorie_id, langue_id
FROM t_assur_zen_communication_template
WHERE code = 'RENVOI_ACCES_PARTENAIRE';

-- -------------------------------------------------------------------------------------
-- RETOUR ARRIERE (uniquement en cas de probleme) :
-- UPDATE t_assur_zen_notificateur_sms n
-- JOIN bak_20260924_notificateur_sms_avis_echeance b ON b.id = n.id
-- SET n.message = b.message
-- WHERE n.statut_envoi = 'en_attente';
--
-- Une fois valide : DROP TABLE bak_20260924_notificateur_sms_avis_echeance;
-- -------------------------------------------------------------------------------------
