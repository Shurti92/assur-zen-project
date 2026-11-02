-- Module Gestion des Sorties de Fonds, Phase 1.
-- Hibernate (ddl-auto=update) cree les tables t_assur_zen_fund_outflows/loans/... automatiquement
-- au premier demarrage du gateway (tables entierement nouvelles). Ce script seede les 9 types de
-- sortie de fonds (§4 du cahier des charges) - meme approche que le seed manuel de GedDocumentType/
-- ProductType. OTHER est marque systeme=1 (repli automatique, non supprimable).
-- A appliquer manuellement une fois, apres le premier demarrage du gateway qui cree les tables.

INSERT INTO t_assur_zen_fund_outflow_types (uuid, code, nom, description, ordre, actif, systeme, created_at, updated_at, deleted, version) VALUES
    (UUID(), 'LOAN', 'Pret partenaire', NULL, 10, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'ADVANCE', 'Avance', NULL, 20, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'EXPENSE', 'Depense', NULL, 30, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'SUPPLIER_PAYMENT', 'Reglement fournisseur', NULL, 40, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'REFUND', 'Remboursement', NULL, 50, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'MISSION_EXPENSE', 'Frais de mission', NULL, 60, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'EXCEPTIONAL_COMMISSION', 'Commission exceptionnelle', NULL, 70, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'PURCHASE', 'Achat', NULL, 80, 1, 0, NOW(6), NOW(6), 0, 0),
    (UUID(), 'OTHER', 'Autre', 'Type de repli par defaut', 999, 1, 1, NOW(6), NOW(6), 0, 0);
