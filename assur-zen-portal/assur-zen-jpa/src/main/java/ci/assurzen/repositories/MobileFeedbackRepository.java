package ci.assurzen.repositories;

import ci.assurzen.entities.MobileFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MobileFeedbackRepository extends JpaRepository<MobileFeedback, Long> {

    List<MobileFeedback> findTop200ByOrderByCreatedAtDesc();

    List<MobileFeedback> findByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
