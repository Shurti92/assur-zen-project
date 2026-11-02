package ci.assurzen.repositories;

import ci.assurzen.entities.RecompenseEchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecompenseEchangeRepository extends JpaRepository<RecompenseEchange, Long> {

    List<RecompenseEchange> findByCompteFideliteIdOrderByCreatedAtDesc(Long compteFideliteId);
}
