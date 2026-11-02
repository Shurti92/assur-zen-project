package ci.assurzen.repositories;

import ci.assurzen.entities.PartenaireProduitCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartenaireProduitCommissionRepository extends JpaRepository<PartenaireProduitCommission, Long> {

    List<PartenaireProduitCommission> findByProduitId(Long produitId);
}
