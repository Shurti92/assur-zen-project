package ci.assurzen.repositories;

import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.ProduitCommissionTaux;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProduitCommissionTauxRepository extends JpaRepository<ProduitCommissionTaux, Long> {

    Optional<ProduitCommissionTaux> findByProduitIdAndTypePartenaire(Long produitId, PartenaireType typePartenaire);

    List<ProduitCommissionTaux> findAllByProduitId(Long produitId);
}
