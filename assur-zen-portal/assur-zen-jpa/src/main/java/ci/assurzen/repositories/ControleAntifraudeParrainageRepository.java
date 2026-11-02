package ci.assurzen.repositories;

import ci.assurzen.entities.ControleAntifraudeParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ControleAntifraudeParrainageRepository extends JpaRepository<ControleAntifraudeParrainage, Long> {

    List<ControleAntifraudeParrainage> findByParrainageIdOrderByCreatedAtAsc(Long parrainageId);
}
