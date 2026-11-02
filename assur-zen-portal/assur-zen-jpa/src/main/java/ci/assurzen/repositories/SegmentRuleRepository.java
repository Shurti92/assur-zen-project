package ci.assurzen.repositories;

import ci.assurzen.entities.SegmentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SegmentRuleRepository extends JpaRepository<SegmentRule, Long> {

    List<SegmentRule> findBySegmentIdOrderByGroupeAscOrdreAsc(Long segmentId);

    Optional<SegmentRule> findByIdAndSegmentId(Long id, Long segmentId);
}
