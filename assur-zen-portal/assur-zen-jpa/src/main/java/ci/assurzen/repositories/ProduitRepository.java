package ci.assurzen.repositories;

import ci.assurzen.entities.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProduitRepository extends JpaRepository<Produit, Long> {

    Optional<Produit> findByNomProduitIgnoreCase(String nomProduit);

    List<Produit> findByProductTypeId(Long productTypeId);

    long countByProductTypeId(Long productTypeId);

    long countByProductTypeIdAndActiveTrue(Long productTypeId);
}
