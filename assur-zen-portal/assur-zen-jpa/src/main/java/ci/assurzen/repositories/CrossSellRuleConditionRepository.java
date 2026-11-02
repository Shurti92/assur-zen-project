package ci.assurzen.repositories;

import ci.assurzen.entities.CrossSellRuleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrossSellRuleConditionRepository extends JpaRepository<CrossSellRuleCondition, Long> {

    List<CrossSellRuleCondition> findByRegleIdOrderByOrdreAsc(Long regleId);

    Optional<CrossSellRuleCondition> findByIdAndRegleId(Long id, Long regleId);
}
