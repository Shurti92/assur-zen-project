package ci.assurzen.repositories;

import ci.assurzen.entities.SequenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SequenceRepository extends JpaRepository<SequenceEntity, Long> {
}
