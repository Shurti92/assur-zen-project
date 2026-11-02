package ci.assurzen.repositories;

import ci.assurzen.entities.CrossSellOpportunity;
import ci.assurzen.entities.CrossSellStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrossSellOpportunityRepository extends JpaRepository<CrossSellOpportunity, Long> {

    Optional<CrossSellOpportunity> findByCode(String code);

    List<CrossSellOpportunity> findByInsuredId(Long insuredId);

    List<CrossSellOpportunity> findByInsuredIdAndProduitRecommandeIdAndStatutNotIn(Long insuredId, Long produitId, List<CrossSellStatut> statuts);
}
