-- Notifications push de l'application mobile (Firebase Cloud Messaging) - 2026-11-03.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate (ddl-auto=update)
-- cree seul les tables ci-dessous. Ce script ne fait que VERIFIER. Aucune donnee existante n'est modifiee.
--
-- SECURITE / EXPLOITATION :
--  * Le fichier du compte de service Firebase reste HORS du depot ; il est indique au gateway par la variable
--    d'environnement FIREBASE_CREDENTIALS_PATH (chemin du fichier JSON, lisible par le service uniquement).
--  * L'envoi reel est COUPE par defaut : cle de configuration PUSH_ENABLED = false (creee au demarrage). La passer a true
--    dans l'ecran de configuration du back-office, sans redemarrage, une fois le compte de service en place.
--  * Verification de la configuration par un administrateur : GET /admin/mobile/push/status (aucun message envoye).

-- 0) Controle : les tables existent. Attendu : 2 lignes.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_assur_zen_mobile_device', 't_assur_zen_mobile_notification_pref');

-- 1) Unicite du jeton d'appareil et de la preference par utilisateur et categorie. Attendu : 2 lignes ;
--    sinon creer les contraintes ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND index_name IN ('uk_mobile_device_token', 'uk_mobile_notification_pref');
-- ALTER TABLE t_assur_zen_mobile_device ADD CONSTRAINT uk_mobile_device_token UNIQUE (token);
-- ALTER TABLE t_assur_zen_mobile_notification_pref ADD CONSTRAINT uk_mobile_notification_pref UNIQUE (user_id, category);

-- 2) La cle PUSH_ENABLED existe et vaut false tant que l'envoi n'est pas voulu.
SELECT config_key, config_value FROM t_assur_zen_dynamic_configuration WHERE config_key = 'PUSH_ENABLED';

-- 3) Appareils actifs enregistres (aucun jeton n'est affiche).
SELECT platform, COUNT(*) AS appareils_actifs FROM t_assur_zen_mobile_device WHERE active = 1 GROUP BY platform;
