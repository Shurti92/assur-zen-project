-- TypeProfile de test
-- Pas de prefixe de schema en dur : cible toujours la base selectionnee par le datasource actif
-- (assur_zen_ci_db en PROD, assur_zen_ci_db_test en TEST), cf. application-{profile}.yml.
INSERT INTO t_assur_zen_type_profile
(libelle, active)
VALUES
    ('ADMIN', true);

-- Utilisateur de test (login: 0757873487 / mot de passe: 123456789)
INSERT INTO t_assur_zen_users
(username, password, roles, first_cnx, active)
VALUES
    ('0757873487', '$2a$10$acoIsBt0Smkxk/Hm80BUtOZCzZcd/cvUp..xZxIohKMnHZ39V8vm2', 'ADMIN', 1, true);

-- Paliers de fidelite par defaut (module Fidelisation) — seuils arbitraires, ajustables via
-- l'API PUT /fidelite/paliers/{id}. La contrainte uk_palier_code rend ces inserts idempotents
-- (silencieusement ignores au redemarrage grace a sql.init.continue-on-error=true).
INSERT INTO t_assur_zen_palier_fidelite
(code, libelle, seuil_points, active, created_at, last_update)
VALUES
    ('BRONZE', 'Bronze', 0, true, NOW(), NOW()),
    ('ARGENT', 'Argent', 5000, true, NOW(), NOW()),
    ('OR', 'Or', 15000, true, NOW(), NOW()),
    ('PLATINE', 'Platine', 40000, true, NOW(), NOW());

-- Langues et categories de template par defaut (module Segments/Templates) — meme idempotence
-- via contrainte UNIQUE(code) + continue-on-error. 'version' fige a 0 : @Version de Hibernate ne
-- s'applique qu'aux ecritures via JPA, un INSERT SQL brut doit le fournir explicitement.
INSERT INTO t_assur_zen_communication_language
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('fr', 'Francais', true, NOW(), NOW(), false, 0),
    ('en', 'Anglais', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('RELANCE', 'Relance', true, NOW(), NOW(), false, 0),
    ('BIENVENUE', 'Bienvenue', true, NOW(), NOW(), false, 0),
    ('FIDELITE', 'Fidelite', true, NOW(), NOW(), false, 0);

-- Types de produit par defaut (module Configuration Produits) — meme idempotence via
-- UNIQUE(code)/UNIQUE(nom) + continue-on-error. AUTRES est marque "systeme" (non supprimable) :
-- c'est le type de repli applique aux produits existants lors de la migration ci-dessous, et par
-- ProductTypeService.resolveOuCreerTypeParDefaut() si jamais absent au demarrage.
INSERT INTO t_assur_zen_product_type
(uuid, code, nom, ordre, actif, systeme, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'AUTO', 'Auto', 1, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'MOTO', 'Moto', 2, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'RISQUE_PARTICULIER', 'Risque du particulier', 3, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'ENTREPRISE', 'Entreprise', 4, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'FLOTTE', 'Flotte', 5, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'MRP', 'MRP', 6, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'VIE', 'Vie', 7, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'EPARGNE', 'Epargne', 8, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'RETRAITE', 'Retraite', 9, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'MIXTE', 'Mixte', 10, true, false, NOW(), NOW(), false, 0),
    (UUID(), 'AUTRES', 'Autres', 999, true, true, NOW(), NOW(), false, 0);

-- Migration : rattache au type "AUTRES" tout produit existant cree avant l'introduction de
-- product_type_id (colonne nullable au niveau schema par prudence, cf. lecon ddl-auto qui ne
-- resserre jamais une contrainte existante — voir ProductType.java). Idempotent (WHERE IS NULL).
UPDATE t_assur_zen_produit
SET product_type_id = (SELECT id FROM t_assur_zen_product_type WHERE code = 'AUTRES' LIMIT 1)
WHERE product_type_id IS NULL;

-- Programme de parrainage par defaut (module Fidelisation/Parrainage) — meme idempotence via
-- UNIQUE(code) + continue-on-error. Pas de uuid/deleted/version : ce module suit le pattern
-- Fidelisation/CrossSell (createdAt/lastUpdate seuls), pas AuditableEntity. 90 jours de validite,
-- 10 parrainages/recompenses max par client, aucun produit restreint (liste vide = tous eligibles).
INSERT INTO t_assur_zen_programme_parrainage
(code, nom, description, statut, max_parrainages_par_client, max_recompenses_par_client, delai_carence_jours, duree_validite_jours, created_at, last_update)
VALUES
    ('PROGRAMME_DEFAUT', 'Programme de parrainage par defaut', 'Parrainez un proche et gagnez des recompenses', 'ACTIF', 10, 10, 0, 90, NOW(), NOW());

-- Regles de recompense par defaut : 5000 XOF pour le parrain, 10% de reduction pour le filleul
-- (exemple du besoin metier d'origine). Configurables ensuite via PUT /parrainage/regles-recompense.
INSERT INTO t_assur_zen_regle_recompense_parrainage
(programme_id, nom, type_recompense, beneficiaire, montant_fixe, priorite, actif, created_at, last_update)
VALUES
    ((SELECT id FROM t_assur_zen_programme_parrainage WHERE code = 'PROGRAMME_DEFAUT' LIMIT 1),
     'Prime de parrainage', 'CASHBACK', 'PARRAIN', 5000, 0, true, NOW(), NOW());

INSERT INTO t_assur_zen_regle_recompense_parrainage
(programme_id, nom, type_recompense, beneficiaire, pourcentage, priorite, actif, created_at, last_update)
VALUES
    ((SELECT id FROM t_assur_zen_programme_parrainage WHERE code = 'PROGRAMME_DEFAUT' LIMIT 1),
     'Reduction de bienvenue filleul', 'REDUCTION_PRIME', 'FILLEUL', 10, 1, true, NOW(), NOW());

-- Configurations dynamiques transverses par defaut (Dynamic Configuration Engine) — meme
-- idempotence via UNIQUE(config_key) + continue-on-error. Volontairement limite aux parametres
-- reellement transverses : Parrainage/CrossSell gardent leurs propres tables de parametres
-- (t_assur_zen_parametre_parrainage / t_assur_zen_cross_sell_parameter) plutot que d'etre migres
-- ici, pour ne pas creer une deuxieme source de verite pour les memes reglages.
-- Cablage reel (audit 2026-08-09, complete 2026-08-10) : MAX_FILE_SIZE (ReclamationService +
-- SinistreService), SESSION_TIMEOUT (AuthService.issueTokens), SMS_ENABLED/EMAIL_ENABLED
-- (ProviderService non-OTP / CampagneEnvoiService / ParrainageService / MailService), OTP_*
-- (OtpService), FIDELITE_* (FideliteService), MAX_LOGIN_ATTEMPTS + ACCOUNT_LOCKOUT_DURATION_MINUTES
-- (verrouillage de compte, AuthService.login/registerFailedAttempt), DEFAULT_LANGUAGE.
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'MAX_LOGIN_ATTEMPTS', '5', 'INTEGER', 'Nombre maximum de tentatives de connexion echouees avant verrouillage du compte', 'SECURITY', 'SECURITY', '5', 'MIN:1;MAX:20', true, true, false, false, true, 0, NOW(), NOW(), false, 0),
    (UUID(), 'SESSION_TIMEOUT', '3600', 'INTEGER', 'Duree de validite du jeton d''acces (secondes)', 'SECURITY', 'AUTHENTICATION', '3600', 'MIN:60', true, true, false, true, true, 1, NOW(), NOW(), false, 0),
    (UUID(), 'SMS_ENABLED', 'true', 'BOOLEAN', 'Active/desactive l''envoi de SMS non critiques (marketing, parrainage, notifications) - n''affecte jamais l''OTP de connexion', 'NOTIFICATION', 'NOTIFICATION', 'true', NULL, true, true, false, false, true, 2, NOW(), NOW(), false, 0),
    (UUID(), 'EMAIL_ENABLED', 'true', 'BOOLEAN', 'Active/desactive globalement l''envoi d''emails', 'NOTIFICATION', 'NOTIFICATION', 'true', NULL, true, true, false, false, true, 3, NOW(), NOW(), false, 0),
    (UUID(), 'MAX_FILE_SIZE', '10485760', 'LONG', 'Taille maximale (octets) d''une piece jointe televersee (reclamations et sinistres)', 'DOCUMENT', 'DOCUMENT', '10485760', 'MIN:1024', true, true, false, false, true, 4, NOW(), NOW(), false, 0),
    (UUID(), 'DEFAULT_LANGUAGE', 'fr', 'STRING', 'Langue par defaut de l''application', 'SYSTEM', 'SYSTEM', 'fr', 'ENUM:fr,en', true, true, false, false, true, 5, NOW(), NOW(), false, 0);

-- Statement separe (et non VALUES supplementaires dans le meme INSERT) : sur une base ou les 6
-- lignes ci-dessus existent deja, un INSERT multi-lignes echoue en bloc des la premiere cle
-- dupliquee (continue-on-error saute l'instruction ENTIERE, pas ligne par ligne), ce qui aurait
-- empeche ces nouvelles cles d'etre semees. Ajoutees le 2026-08-09 lors du cablage de OtpService
-- et FideliteService.
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'OTP_CODE_VALIDITY_MINUTES', '5', 'INTEGER', 'Duree de validite d''un code OTP (minutes)', 'SECURITY', 'AUTHENTICATION', '5', 'MIN:1;MAX:30', true, true, false, false, true, 6, NOW(), NOW(), false, 0),
    (UUID(), 'OTP_MAX_ATTEMPTS', '5', 'INTEGER', 'Nombre maximum de tentatives de saisie d''un code OTP avant invalidation', 'SECURITY', 'AUTHENTICATION', '5', 'MIN:1;MAX:10', true, true, false, false, true, 7, NOW(), NOW(), false, 0),
    (UUID(), 'OTP_RESEND_COOLDOWN_SECONDS', '60', 'INTEGER', 'Delai minimum entre deux demandes de renvoi d''un code OTP (secondes)', 'SECURITY', 'AUTHENTICATION', '60', 'MIN:10;MAX:600', true, true, false, false, true, 8, NOW(), NOW(), false, 0),
    (UUID(), 'OTP_CODE_LENGTH', '6', 'INTEGER', 'Nombre de chiffres d''un code OTP', 'SECURITY', 'AUTHENTICATION', '6', 'MIN:4;MAX:8', true, true, false, false, true, 9, NOW(), NOW(), false, 0),
    (UUID(), 'FIDELITE_POINTS_PAR_TRANCHE_FCFA', '1000', 'LONG', 'Montant de prime payee (FCFA) donnant droit a 1 point de fidelite', 'LOYALTY', 'LOYALTY', '1000', 'MIN:1', true, true, false, false, true, 10, NOW(), NOW(), false, 0),
    (UUID(), 'FIDELITE_BONUS_RENOUVELLEMENT', '500', 'LONG', 'Points de fidelite bonus accordes au renouvellement d''un contrat', 'LOYALTY', 'LOYALTY', '500', 'MIN:0', true, true, false, false, true, 11, NOW(), NOW(), false, 0);

-- Statement separe pour la meme raison que ci-dessus (idempotence sur une base ou les cles
-- precedentes existent deja). Ajoutee le 2026-08-10 : contrat "Verrouillage de compte apres echecs
-- de connexion" (AuthService.registerFailedAttempt).
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'ACCOUNT_LOCKOUT_DURATION_MINUTES', '15', 'INTEGER', 'Duree du verrouillage d''un compte apres MAX_LOGIN_ATTEMPTS echecs de connexion (minutes)', 'SECURITY', 'SECURITY', '15', 'MIN:1;MAX:1440', true, true, false, false, true, 12, NOW(), NOW(), false, 0);

-- Statement separe, meme raison d'idempotence deja documentee ci-dessus. Bareme de la prime d'objectif
-- partenaire (contrat "Prime d'objectif partenaire", 2026-08-28, PrimeObjectifService) : seuils en %
-- de taux de realisation, taux en % applique - effet de seuil assume (chaque palier s'applique a TOUT
-- le depassement depuis l'objectif de base, pas seulement depuis son propre seuil). "category" doit
-- rester une valeur du enum ConfigCategory (module, lui, est un String libre) - aucune valeur existante
-- ne correspond au domaine "prime partenaire", d'ou OTHER (confirme en live : 'PRIME_OBJECTIF' comme
-- category fait echouer la lecture avec "No enum constant ConfigCategory.PRIME_OBJECTIF").
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'PRIME_OBJECTIF_SEUIL_BASE', '100', 'INTEGER', 'Taux de realisation minimum (%) pour declencher la prime d''objectif', 'PRIME_OBJECTIF', 'OTHER', '100', 'MIN:1', true, true, false, false, true, 13, NOW(), NOW(), false, 0),
    (UUID(), 'PRIME_OBJECTIF_TAUX_BASE', '5', 'INTEGER', 'Taux de prime (%) applique a l''objectif des que le seuil de base est atteint', 'PRIME_OBJECTIF', 'OTHER', '5', 'MIN:0', true, true, false, false, true, 14, NOW(), NOW(), false, 0),
    (UUID(), 'PRIME_OBJECTIF_SEUIL_PALIER2', '110', 'INTEGER', 'Taux de realisation (%) declenchant le 2e palier de prime', 'PRIME_OBJECTIF', 'OTHER', '110', 'MIN:1', true, true, false, false, true, 15, NOW(), NOW(), false, 0),
    (UUID(), 'PRIME_OBJECTIF_TAUX_PALIER2', '6', 'INTEGER', 'Taux de prime (%) applique au depassement de l''objectif au 2e palier', 'PRIME_OBJECTIF', 'OTHER', '6', 'MIN:0', true, true, false, false, true, 16, NOW(), NOW(), false, 0),
    (UUID(), 'PRIME_OBJECTIF_SEUIL_PALIER3', '125', 'INTEGER', 'Taux de realisation (%) declenchant le 3e palier de prime', 'PRIME_OBJECTIF', 'OTHER', '125', 'MIN:1', true, true, false, false, true, 17, NOW(), NOW(), false, 0),
    (UUID(), 'PRIME_OBJECTIF_TAUX_PALIER3', '7', 'INTEGER', 'Taux de prime (%) applique au depassement de l''objectif au 3e palier', 'PRIME_OBJECTIF', 'OTHER', '7', 'MIN:0', true, true, false, false, true, 18, NOW(), NOW(), false, 0);

-- Modeles de communication SMS de bienvenue (contrat "Creation de comptes utilisateurs", 2026-08-11)
-- - reutilises par SmsNotificationService.sendFromTemplate() (nouveau mecanisme generique, premiere
-- utilisation reelle de TemplateVariableResolver.resoudreAvecValeurs() hors apercu admin). Categorie
-- BIENVENUE et langue fr deja seedees plus haut dans ce fichier. Statut ACTIF direct (pas BROUILLON)
-- pour etre immediatement utilisables sans etape d'activation manuelle en base fraiche.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'BIENVENUE_PARTENAIRE_MOBILE', 'Bienvenue partenaire (acces mobile)',
     'SMS envoye automatiquement a la creation du compte d''un partenaire REPRESENTANT (PartenaireService.tenterCreationCompteAutomatique) - {{lienApplication}} vient de app.partenaire.lien-connexion (prod vs sandbox, contrat "Lien application dans le SMS de bienvenue partenaire", 2026-08-30).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Bienvenue {{nomPartenaire}} dans le reseau partenaire AssurZen ! Connectez-vous sur {{lienApplication}} avec le numero {{username}} et le mot de passe {{motDePasse}}. Vous devrez le modifier a la premiere connexion. AssurZen Assurance',
     'ACTIF', true, NOW(), NOW(), false, 0),
    (UUID(), 'BIENVENUE_BACKOFFICE', 'Bienvenue back-office',
     'SMS envoye automatiquement a la creation manuelle d''un compte back-office par un administrateur (UserProfileService.create)',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : Votre compte back-office AssurZen a ete cree. Identifiant : {{username}}, mot de passe temporaire : {{motDePasse}}. Connectez-vous sur le back-office et changez votre mot de passe des la premiere connexion.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Statement separe (et non VALUES supplementaire dans l'INSERT ci-dessus) pour la meme raison que
-- deja documentee plus haut dans ce fichier pour les cles de configuration dynamique : sur une base
-- ou BIENVENUE_PARTENAIRE_MOBILE/BIENVENUE_BACKOFFICE existent deja, un INSERT multi-lignes echoue
-- en bloc des la premiere cle dupliquee (continue-on-error saute l'instruction ENTIERE), ce qui
-- aurait empeche ce nouveau modele d'etre seme. Ajoute le 2026-08-25 (contrat "Renvoi automatique
-- des acces (SMS) a un partenaire deja cree") - reutilise par UserProfileService.resendAccess().
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'RENVOI_ACCES_BACKOFFICE', 'Renvoi des acces (back-office)',
     'SMS envoye a la demande d''un administrateur pour renvoyer les acces d''un compte deja existant (UserProfileService.resendAccess) - regenere un mot de passe temporaire, distinct du modele de creation initiale (BIENVENUE_BACKOFFICE) pour un libelle coherent avec un renvoi et non une premiere creation. Utilise pour les comptes NON partenaires uniquement (cf. RENVOI_ACCES_PARTENAIRE ci-dessous) - pas de lien applicatif ici, ce modele est partage avec des profils qui n''ont pas d''espace partenaire.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Vos acces AssurZen ont ete renvoyes. Identifiant : {{username}}, nouveau mot de passe temporaire : {{motDePasse}}. Connectez-vous et changez-le des que possible.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Renvoi d'acces PARTENAIRE (contrat "Lien application dans le SMS de bienvenue partenaire" §2,
-- 2026-08-30) - distinct de RENVOI_ACCES_BACKOFFICE ci-dessus : seul ce modele inclut {{lienApplication}}
-- (UserProfileService.resendAccess() choisit l'un ou l'autre selon que le compte est lie a un Partenaire).
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'RENVOI_ACCES_PARTENAIRE', 'Renvoi des acces (partenaire)',
     'SMS envoye a la demande d''un administrateur pour renvoyer les acces d''un compte PARTENAIRE deja existant (UserProfileService.resendAccess) - {{lienApplication}} vient de app.partenaire.lien-connexion (prod vs sandbox).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Vos acces AssurZen ont ete renvoyes. Connectez-vous sur {{lienApplication}} avec l''identifiant : {{username}} et le nouveau mot de passe temporaire : {{motDePasse}}. Changez-le des que possible.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Bienvenue COMMERCIAL (2026-09-15, gap remonte par l'utilisateur : le SMS de creation d'un commercial
-- (CommercialService.create() -> UserProfileService.create(), meme mecanisme que tout compte
-- back-office) n'incluait jamais de lien, contrairement au partenaire - alors que le commercial doit
-- lui aussi se connecter a l'Espace Partenaire (meme lien que app.partenaire.lien-connexion, prod vs
-- sandbox vs dev). Statement separe (meme raison que les autres modeles de ce fichier : un INSERT
-- multi-lignes echoue en bloc des la premiere cle dupliquee).
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'BIENVENUE_COMMERCIAL', 'Bienvenue commercial',
     'SMS envoye automatiquement a la creation d''un compte Commercial (CommercialService.create() -> UserProfileService.create()) - {{lienApplication}} vient de app.partenaire.lien-connexion (prod vs sandbox vs dev), meme propriete que pour le partenaire. Contenu final valide par l''utilisateur le 2026-09-15 (un premier essai avec emojis avait ete corrompu en "?" via ce seed SQL brut - retire au profit de puces "-" simples, sans risque d''encodage).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'BIENVENUE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : Votre compte Espace Partenaire AssurZen a ete cree avec succes.\n- Identifiant : {{username}}\n- Mot de passe temporaire : {{motDePasse}}\nConnectez-vous sur {{lienApplication}} et changez votre mot de passe des votre premiere connexion pour securiser votre compte.\nMerci et bonne utilisation.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Categorie COMMISSION (contrat "SMS de confirmation au partenaire lors du paiement d'une
-- commission", 2026-08-25) - statement separe des categories RELANCE/BIENVENUE/FIDELITE deja
-- seedees plus haut, meme raison d'idempotence deja documentee (INSERT multi-lignes echoue en
-- bloc si execute sur une base ou ces 3-la existent deja).
INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('COMMISSION', 'Commission', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_PAYEE', 'Commission payee',
     'SMS envoye au partenaire a chaque paiement reussi de commission (CommissionService.pay) - UN SEUL SMS agrege par partenaire distinct concerne dans le meme appel, meme si plusieurs contrats sont payes en une fois, jamais un SMS par contrat.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : votre commission de {{montant}} FCFA ({{nombreContrats}} contrat(s)) a ete versee. Ref : {{reference}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- SMS "demande recue", envoye AVANT traitement (contrat "Notification avant/apres paiement de
-- commission", 2026-08-30) - distinct de COMMISSION_PAYEE ci-dessus qui reste envoye APRES paiement
-- effectif ; le partenaire recoit donc les deux SMS pour un seul appel a POST /commissions/pay.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_DEMANDE_RECUE', 'Demande de paiement de commission recue',
     'SMS envoye au partenaire des la reception d''une demande de paiement de commission (CommissionService.pay, AVANT tout enregistrement) - UN SEUL SMS agrege par partenaire distinct concerne, meme convention que COMMISSION_PAYEE.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : votre demande de paiement de commission de {{montant}} FCFA ({{nombreContrats}} contrat(s)) a ete recue et est en cours de traitement. Ref : {{reference}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Paiement des commissions partenaire selon la frequence (2026-09-23) - un statement par modele,
-- meme raison d'idempotence que ci-dessus (code unique : un re-run en dev echoue sans effet).
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

-- Categorie + modele PRIME_OBJECTIF (contrat "Prime d'objectif partenaire", 2026-08-28) - statements
-- separes, meme raison d'idempotence deja documentee ci-dessus.
INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('PRIME_OBJECTIF', 'Prime objectif', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'PRIME_OBJECTIF_PAYEE', 'Prime objectif payee',
     'SMS envoye au partenaire quand sa prime d''objectif annuel est marquee PAYEE (PrimeObjectifService.changerStatut) - une prime par (partenaire, exercice), jamais agrege.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'PRIME_OBJECTIF' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : votre prime d''objectif {{exercice}} de {{montant}} FCFA a ete versee. Ref : {{reference}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Bareme du bonus mensuel Commercial (contrat "Module Commercial", 2026-09-07,
-- CommercialPerformanceService) - statement separe, meme raison d'idempotence deja documentee
-- ci-dessus (continue-on-error saute l'instruction ENTIERE en cas de doublon). Bonus = objectif
-- mensuel x COMMERCIAL_BONUS_RATE% des lors que le taux d'atteinte >= COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE
-- (toujours calcule sur l'objectif, jamais sur le realise). Categorie OTHER : meme raison que
-- PRIME_OBJECTIF_* ci-dessus (aucune categorie ConfigCategory ne correspond au domaine "commercial").
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMERCIAL_BONUS_ENABLED', 'true', 'BOOLEAN', 'Active/desactive le calcul du bonus mensuel des commerciaux', 'COMMERCIAL', 'OTHER', 'true', NULL, true, true, false, false, true, 19, NOW(), NOW(), false, 0),
    (UUID(), 'COMMERCIAL_BONUS_RATE', '5', 'DECIMAL', 'Taux de bonus (%) applique a l''objectif mensuel des que le seuil d''atteinte est atteint', 'COMMERCIAL', 'OTHER', '5', 'MIN:0', true, true, false, false, true, 20, NOW(), NOW(), false, 0),
    (UUID(), 'COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE', '100', 'DECIMAL', 'Taux d''atteinte minimum (%) de l''objectif mensuel pour declencher le bonus commercial', 'COMMERCIAL', 'OTHER', '100', 'MIN:1', true, true, false, false, true, 21, NOW(), NOW(), false, 0);

-- Categorie + modele BONUS_COMMERCIAL (contrat "Module Commercial", 2026-09-07) - statements
-- separes, meme raison d'idempotence deja documentee ci-dessus.
INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('COMMERCIAL', 'Commercial', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'BONUS_COMMERCIAL_PAYE', 'Bonus commercial paye',
     'SMS envoye au commercial quand son bonus mensuel est marque PAID (CommercialPerformanceService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMERCIAL' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : votre bonus commercial de {{mois}} d''un montant de {{montant}} FCFA a ete verse. Felicitations !',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Commission commerciale par contrat (contrat "Commission commerciale interne", 2026-09-08) -
-- reutilise la categorie COMMISSION deja seedee plus haut (module Commission partenaire), statement
-- separe meme raison d'idempotence deja documentee ci-dessus.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_COMMERCIALE_PAYEE', 'Commission commerciale payee',
     'SMS envoye au commercial interne a chaque paiement reussi de commission par contrat (CommercialCommissionService.pay) - UN SEUL SMS agrege par commercial distinct concerne dans le meme appel.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : votre commission de {{montant}} FCFA ({{nombreContrats}} contrat(s)) a ete versee. Ref : {{reference}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- SMS d'incorporation de vehicule(s) dans une flotte (contrat "SMS d'incorporation flotte",
-- 2026-09-08) - envoye au gestionnaire de la flotte (Fleet.managerPhone) ou, a defaut, au client
-- (Fleet.client.clientPhone). Utilise par l'import en masse (nombreVehicules=succes,
-- VehiculeImportService.confirm, UN SEUL SMS agrege pour tout le lot importe, pas un SMS par ligne -
-- meme convention d'agregation que COMMISSION_PAYEE/COMMISSION_COMMERCIALE_PAYEE ci-dessus). Depuis le
-- 2026-09-13, l'ajout UNITAIRE (FleetService.addVehicle) utilise un modele distinct et plus detaille,
-- VEHICULE_FLOTTE_DETAIL ci-dessous (detailler chaque vehicule n'a de sens que pour un ajout
-- a la fois, jamais pour un lot importe).
INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('FLOTTE', 'Flotte', true, NOW(), NOW(), false, 0);

-- Chaque template dans son propre INSERT (pas de VALUES multi-lignes) : sur une base ou
-- VEHICULE_INCORPORE_FLOTTE existe deja (redemarrage, pas une base neuve), un conflit de cle unique
-- sur UNE ligne d'un INSERT multi-lignes fait echouer TOUT le statement, empechant la ligne DETAIL
-- (nouvelle) d'etre inseree meme si elle n'est elle-meme en conflit avec rien. Bug reel constate.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VEHICULE_INCORPORE_FLOTTE', 'Vehicule(s) incorpore(s) a la flotte (lot)',
     'SMS envoye au gestionnaire (ou a defaut au client) a chaque import en masse de vehicules dans une flotte (VehiculeImportService.confirm, un seul SMS agrege pour tout le lot).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'FLOTTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : {{nombreVehicules}} vehicule(s) incorpore(s) a la flotte {{flotteNom}} ({{flotteNumero}}).',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VEHICULE_FLOTTE_DETAIL', 'Vehicule incorpore a la flotte (detail)',
     'SMS envoye au gestionnaire (ou a defaut au client) a chaque ajout UNITAIRE d''un vehicule/moto dans une flotte (FleetService.addVehicle) - detaille type/marque/modele et n de police si un contrat existe deja pour ce vehicule.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'FLOTTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'AssurZen : {{typeVehicule}} {{marque}} {{modele}} incorpore a la flotte {{flotteNom}} ({{flotteNumero}}){{policeSuffix}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Commission apporteur vignette (contrat "commission apporteur vignette", 2026-09-15, contenu revu
-- le 2026-09-16 sur demande utilisateur) - forfait du a un partenaire ou un commercial interne qui
-- apporte une vente de vignette moto (VignetteCommissionService.payer). UN SMS PAR VIGNETTE payee
-- (pas d'agregation, contrairement a COMMISSION_PAYEE/COMMISSION_COMMERCIALE_PAYEE) : le contenu
-- inclut les details de LA moto concernee, qui n'ont de sens que pour une vignette a la fois.
-- Statement separe, meme raison d'idempotence deja documentee dans ce fichier.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'COMMISSION_VIGNETTE_PAYEE', 'Commission vignette payee',
     'SMS envoye a l''apporteur (partenaire ou commercial interne) d''une demande de vignette moto quand sa commission forfaitaire est payee (VignetteCommissionService.payer) - UN SMS PAR VIGNETTE, jamais agrege (inclut les details de la moto concernee).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'COMMISSION' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Une commission de {{montant}} FCFA vous a ete versee pour 1 vignette de la moto aux informations ci-dessous :\nMoto : {{marque}} {{modele}}\nChassis : {{numeroChassis}}\nRef. paiement : {{reference}}.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Montant de la commission apporteur vignette, variable par zone de LIVRAISON de la demande
-- (contrat "Commission apporteur variable par zone", 2026-09-18, remplace l'ancien forfait unique
-- VIGNETTE_COMMISSION_APPORTEUR_MONTANT). Egalement deduit du montant facture au client (cf.
-- DemandeVignetteMotoService.creer -> reductionApporteur). Statements separes, meme raison
-- d'idempotence deja documentee dans ce fichier.
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VIGNETTE_COMMISSION_APPORTEUR_MONTANT_ABIDJAN', '500', 'DECIMAL',
     'Montant (FCFA) credite en commission a l''apporteur pour une demande de vignette moto livree a Abidjan - egalement deduit du montant facture au client',
     'VIGNETTE', 'OTHER', '500', 'MIN:0', true, true, false, false, true, 22, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VIGNETTE_COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN', '1000', 'DECIMAL',
     'Montant (FCFA) credite en commission a l''apporteur pour une demande de vignette moto livree hors Abidjan - egalement deduit du montant facture au client',
     'VIGNETTE', 'OTHER', '1000', 'MIN:0', true, true, false, false, true, 23, NOW(), NOW(), false, 0);

-- Suivi d'evolution des demandes de vignettes + suivi public securise par token (contrat du
-- 2026-09-17). Categorie dediee VIGNETTE (n'existait pas encore - COMMISSION_VIGNETTE_PAYEE plus
-- haut reutilise COMMISSION car c'est un message de commission, pas de suivi de cycle de vie).
INSERT INTO t_assur_zen_communication_template_category
(code, libelle, active, created_at, updated_at, deleted, version)
VALUES
    ('VIGNETTE', 'Vignette Moto', true, NOW(), NOW(), false, 0);

-- Un template par statut (code = VIGNETTE_MOTO_<NOM_STATUT>, resolu dynamiquement par
-- DemandeVignetteMotoService - jamais code en dur ailleurs). Seul le template BROUILLON (creation)
-- inclut {{lienSuivi}} : c'est le seul moment ou le token brut existe encore en memoire pour etre
-- inclus dans un SMS (LienSuiviVignette ne persiste que son hash SHA-256, jamais le token en clair) -
-- tous les changements de statut ulterieurs renvoient vers le lien deja recu sans repeter l'URL.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_BROUILLON', 'Vignette moto - demande creee',
     'SMS envoye a la creation d''une demande de vignette moto (DemandeVignetteMotoService.creer) - seul message du cycle a inclure le lien de suivi complet.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre demande de vignette {{reference}} a ete enregistree avec succes. Suivez son evolution ici : {{lienSuivi}}',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_EN_ATTENTE_PAIEMENT', 'Vignette moto - en attente de paiement',
     'SMS envoye quand une demande de vignette moto est soumise (DemandeVignetteMotoService.soumettre).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre demande {{reference}} est en attente de paiement. Consultez son evolution via le lien recu par SMS a la creation de votre demande.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_PAYEE', 'Vignette moto - paiement confirme',
     'SMS envoye quand une demande de vignette moto passe au statut PAYEE (DemandeVignetteMotoService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre paiement pour la demande {{reference}} a ete confirme. Votre demande est en cours de traitement.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_EN_TRAITEMENT', 'Vignette moto - en cours de traitement',
     'SMS envoye quand une demande de vignette moto passe au statut EN_TRAITEMENT (DemandeVignetteMotoService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre demande de vignette {{reference}} est en cours de traitement.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_EN_EDITION', 'Vignette moto - en cours d''edition',
     'SMS envoye quand une demande de vignette moto passe au statut EN_EDITION (DemandeVignetteMotoService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre vignette liee a la demande {{reference}} est en cours d''edition.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_EDITEE', 'Vignette moto - editee',
     'SMS envoye quand une demande de vignette moto passe au statut EDITEE (DemandeVignetteMotoService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre vignette liee a la demande {{reference}} a ete editee avec succes.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_EN_LIVRAISON', 'Vignette moto - en attente de livraison',
     'SMS envoye quand une demande de vignette moto passe au statut EN_LIVRAISON (DemandeVignetteMotoService.changerStatut).',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre vignette {{reference}} est prete. Elle est en attente de remise ou de livraison.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_LIVREE', 'Vignette moto - remise',
     'SMS envoye quand une demande de vignette moto passe au statut LIVREE (DemandeVignetteMotoService.changerStatut) - statut terminal, aucun lien de suivi inclus.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre vignette {{reference}} vous a ete remise. Merci pour votre confiance.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_ANNULEE', 'Vignette moto - annulee',
     'SMS envoye quand une demande de vignette moto est annulee (DemandeVignetteMotoService.annuler) - statut terminal.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre demande de vignette {{reference}} a ete annulee.',
     'ACTIF', true, NOW(), NOW(), false, 0);

INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_REJETEE', 'Vignette moto - rejetee',
     'SMS envoye quand une demande de vignette moto est rejetee (DemandeVignetteMotoService.rejeter, a n''importe quelle etape) - statut terminal, motif dynamique.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Votre demande de vignette {{reference}} n''a pas pu aboutir. Motif : {{motif}}. Pour plus d''informations, contactez AssurZen.',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- SMS de regeneration du lien de suivi (LienSuiviVignetteService.regenerer) - distinct du cycle de
-- statut ci-dessus, seul autre moment ou le token brut existe en memoire pour etre inclus.
INSERT INTO t_assur_zen_communication_template
(uuid, code, nom, description, canal, categorie_id, langue_id, objet, contenu, statut, active, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VGN_MOTO_LIEN_REGENERE', 'Vignette moto - lien de suivi regenere',
     'SMS envoye quand un admin regenere le lien de suivi public d''une demande (LienSuiviVignetteService.regenerer) - invalide implicitement l''ancien lien.',
     'SMS', (SELECT id FROM t_assur_zen_communication_template_category WHERE code = 'VIGNETTE' LIMIT 1),
     (SELECT id FROM t_assur_zen_communication_language WHERE code = 'fr' LIMIT 1), NULL,
     'Voici votre nouveau lien de suivi pour la demande {{reference}} : {{lienSuivi}}',
     'ACTIF', true, NOW(), NOW(), false, 0);

-- Duree de validite (jours) d'un lien de suivi public avant expiration (LienSuiviVignetteService).
INSERT INTO t_assur_zen_dynamic_configuration
(uuid, config_key, config_value, value_type, description, module, category, default_value, validation_rule,
 active, editable, is_sensitive, restart_required, cache_enabled, display_order, created_at, updated_at, deleted, version)
VALUES
    (UUID(), 'VIGNETTE_SUIVI_TOKEN_VALIDITE_JOURS', '90', 'LONG',
     'Duree de validite (en jours) d''un lien de suivi public d''une demande de vignette moto avant expiration automatique',
     'VIGNETTE', 'OTHER', '90', 'MIN:1', true, true, false, false, true, 23, NOW(), NOW(), false, 0);
