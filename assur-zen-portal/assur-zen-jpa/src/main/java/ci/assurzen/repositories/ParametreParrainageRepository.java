package ci.assurzen.repositories;

import ci.assurzen.entities.ParametreParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametreParrainageRepository extends JpaRepository<ParametreParrainage, Long> {

    Optional<ParametreParrainage> findByCle(String cle);
}
