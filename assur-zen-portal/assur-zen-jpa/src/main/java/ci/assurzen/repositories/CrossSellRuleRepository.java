package ci.assurzen.repositories;

import ci.assurzen.entities.CrossSellRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrossSellRuleRepository extends JpaRepository<CrossSellRule, Long> {

    Optional<CrossSellRule> findByCode(String code);

    List<CrossSellRule> findByActifTrueOrderByOrdreAsc();
}
