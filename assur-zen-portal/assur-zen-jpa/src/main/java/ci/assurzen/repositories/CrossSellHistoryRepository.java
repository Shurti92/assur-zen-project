package ci.assurzen.repositories;

import ci.assurzen.entities.CrossSellHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrossSellHistoryRepository extends JpaRepository<CrossSellHistory, Long> {

    List<CrossSellHistory> findByOpportuniteIdOrderByCreatedAtAsc(Long opportuniteId);
}
