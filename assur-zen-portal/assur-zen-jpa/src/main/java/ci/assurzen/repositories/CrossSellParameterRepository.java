package ci.assurzen.repositories;

import ci.assurzen.entities.CrossSellParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CrossSellParameterRepository extends JpaRepository<CrossSellParameter, Long> {

    Optional<CrossSellParameter> findByCle(String cle);
}
