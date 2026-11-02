-- Module GED (Gestion Electronique des Documents), Phase 1.
-- Hibernate (ddl-auto=update) cree les tables t_assur_zen_ged_* automatiquement au premier
-- demarrage du gateway (tables entierement nouvelles). Ce script seede les 15 types de document
-- (§11 du cahier des charges) et les 10 categories (§12) - meme approche que le seed manuel de
-- ProductType. AUTRE/OTHER sont marques systeme=1 (repli automatique, non supprimables).
-- A appliquer manuellement une fois, apres le premier demarrage du gateway qui cree les tables.

INSERT INTO t_assur_zen_ged_document_types (uuid, code, nom, description, ordre, actif, systeme, created_at, updated_at, deleted, version) VALUES
    (UUID(), 'CONTRAT', 'Contrat', NULL, 10, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'ATTESTATION', 'Attestation', NULL, 20, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'AVENANT', 'Avenant', NULL, 30, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'QUITTANCE', 'Quittance', NULL, 40, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'PIECE_IDENTITE', 'Piece d''identite', NULL, 50, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'CARTE_GRISE', 'Carte grise', NULL, 60, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'PERMIS', 'Permis de conduire', NULL, 70, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'VISITE_TECHNIQUE', 'Visite technique', NULL, 80, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'JUSTIFICATIF', 'Justificatif', NULL, 90, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'DECLARATION_SINISTRE', 'Declaration de sinistre', NULL, 100, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'RAPPORT_EXPERT', 'Rapport d''expert', NULL, 110, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'COURRIER', 'Courrier', NULL, 120, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'FACTURE', 'Facture', NULL, 130, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'RELEVE', 'Releve', NULL, 140, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'AUTRE', 'Autre', 'Type de repli par defaut', 999, 1, 1, NOW(6), NOW(6), 0, 0);

INSERT INTO t_assur_zen_ged_document_categories (uuid, code, nom, description, ordre, actif, systeme, created_at, updated_at, deleted, version) VALUES
    (UUID(), 'CONTRACTUAL', 'Contractuel', NULL, 10, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'CLIENT', 'Client', NULL, 20, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'VEHICLE', 'Vehicule', NULL, 30, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'CLAIM', 'Sinistre', NULL, 40, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'PAYMENT', 'Paiement', NULL, 50, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'LEGAL', 'Juridique', NULL, 60, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'ACCOUNTING', 'Comptable', NULL, 70, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'COMMERCIAL', 'Commercial', NULL, 80, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'ADMINISTRATIVE', 'Administratif', NULL, 90, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'OTHER', 'Autre', 'Categorie de repli par defaut', 999, 1, 1, NOW(6), NOW(6), 0, 0);
