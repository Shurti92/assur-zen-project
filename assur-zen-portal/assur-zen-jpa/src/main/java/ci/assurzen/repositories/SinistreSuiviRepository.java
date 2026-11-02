package ci.assurzen.repositories;

import ci.assurzen.entities.SinistreSuivi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SinistreSuiviRepository extends JpaRepository<SinistreSuivi, Long> {

    List<SinistreSuivi> findBySinistreIdOrderByCreatedAtAsc(Long sinistreId);
}
