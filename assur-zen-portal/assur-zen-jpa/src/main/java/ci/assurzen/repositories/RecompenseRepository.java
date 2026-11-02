package ci.assurzen.repositories;

import ci.assurzen.entities.Recompense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecompenseRepository extends JpaRepository<Recompense, Long> {

    Optional<Recompense> findByCode(String code);
}
