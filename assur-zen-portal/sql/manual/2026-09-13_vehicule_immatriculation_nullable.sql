-- Module Gestion de flotte auto/moto - enregistrement moto (2026-09-13).
-- L'immatriculation devient optionnelle (une moto neuve/en attente de plaque doit pouvoir etre
-- enregistree via son numero de chassis uniquement). Hibernate (ddl-auto=update) ajoute la nouvelle
-- colonne "cylindree" automatiquement au demarrage (colonne neuve nullable), mais NE RELACHE JAMAIS
-- une contrainte NOT NULL existante tout seul - a appliquer manuellement une fois, apres le
-- demarrage du gateway qui a deploye ce changement.
-- La cle unique uk_vehicule_immatriculation n'a pas besoin d'etre touchee : MySQL/MariaDB autorise
-- plusieurs lignes NULL dans une colonne UNIQUE (seules les valeurs non-nulles sont dedupliquees).
ALTER TABLE t_assur_zen_vehicules
    MODIFY COLUMN immatriculation VARCHAR(20) NULL;
