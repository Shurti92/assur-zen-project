-- Sinistres mobiles (demande mobile n°6) - 2026-11-02.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) ajoute seul la colonne t_assur_zen_sinistre_photos.kind (nullable). Ce script ne fait que VERIFIER.
-- Aucune donnee existante n'est modifiee : les pieces deja presentes (kind NULL) restent des photos du back-office.

-- 0) Controle : la colonne existe. Attendu : 1 ligne.
SELECT table_name, column_name, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND column_name = 'kind'
  AND table_name IN (SELECT table_name FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name LIKE '%sinistre%photo%');

-- 1) Sinistres declares depuis le mobile (commentaire du premier suivi) :
SELECT s.numero, s.nature, s.date_survenance, s.date_declaration, s.status
FROM t_assur_zen_sinistres s
WHERE s.notes LIKE 'Declaration mobile.%'
ORDER BY s.date_declaration DESC;

-- 2) Cle de configuration optionnelle : MOBILE_MAX_OPEN_CLAIMS_PER_CONTRACT (defaut 5 sinistres ouverts par contrat).
