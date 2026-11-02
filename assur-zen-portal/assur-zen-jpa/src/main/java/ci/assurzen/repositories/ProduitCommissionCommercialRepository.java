package ci.assurzen.repositories;

import ci.assurzen.entities.ProduitCommissionCommercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProduitCommissionCommercialRepository extends JpaRepository<ProduitCommissionCommercial, Long> {

    Optional<ProduitCommissionCommercial> findByProduitId(Long produitId);
}
