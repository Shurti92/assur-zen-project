-- Module Gestion de flotte auto/moto.
-- Hibernate (ddl-auto=update) cree la table t_assur_zen_fleet_vehicles et ses index/FK standards
-- automatiquement au premier demarrage (table entierement nouvelle). Cette colonne generee + sa
-- cle unique appliquent la regle "un vehicule ne peut etre ACTIVE que dans une seule flotte a la
-- fois" : MySQL 8 n'a pas d'index unique partiel/filtre, donc on simule via une colonne STORED qui
-- ne vaut vehicule_id que lorsque status = 'ACTIVE' (NULL sinon - MySQL autorise plusieurs NULL
-- dans un index unique, donc les lignes REMOVED/TRANSFERRED/REPLACED coexistent librement).
-- A appliquer manuellement une fois, apres le premier demarrage du gateway qui cree la table.
ALTER TABLE t_assur_zen_fleet_vehicles
    ADD COLUMN active_vehicule_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN vehicule_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_fleet_vehicle_active_vehicule (active_vehicule_id);
