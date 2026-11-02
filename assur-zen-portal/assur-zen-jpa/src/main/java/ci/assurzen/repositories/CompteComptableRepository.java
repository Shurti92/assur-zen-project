package ci.assurzen.repositories;

import ci.assurzen.entities.CompteComptable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompteComptableRepository extends JpaRepository<CompteComptable, Long> {

    Optional<CompteComptable> findByCode(String code);
}
