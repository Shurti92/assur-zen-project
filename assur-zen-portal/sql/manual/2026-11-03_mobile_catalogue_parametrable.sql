-- Catalogue mobile parametrable depuis le back-office - 2026-11-03.
--
-- A appliquer UNE fois, APRES le premier demarrage du gateway qui deploie ce changement : Hibernate
-- (ddl-auto=update) cree seul la table t_assur_zen_mobile_product_content. Ce script ne fait que VERIFIER.
-- Aucune donnee existante n'est modifiee.
--
-- IMPORTANT : sur un environnement neuf (recette, prod), le catalogue de l'application mobile est VIDE tant qu'aucun produit
-- n'a ete rendu visible depuis le back-office (PUT /admin/mobile/products/{id} avec visibleMobile=true).

-- 0) Controle : la table existe. Attendu : 1 ligne.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_mobile_product_content';

-- 1) Unicite d'un contenu par produit. Attendu : 1 ligne ; sinon creer la contrainte ci-dessous.
SELECT index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 't_assur_zen_mobile_product_content'
  AND index_name = 'uk_mobile_product_content_product';
-- ALTER TABLE t_assur_zen_mobile_product_content ADD CONSTRAINT uk_mobile_product_content_product UNIQUE (product_id);

-- 2) Produits visibles sur le mobile, dans l'ordre d'affichage :
SELECT c.product_id, p.nom_produit, c.visible_mobile, c.display_order
FROM t_assur_zen_mobile_product_content c
JOIN t_assur_zen_produit p ON p.id = c.product_id
ORDER BY c.display_order;
