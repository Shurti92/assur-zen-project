package ci.assurzen.repositories;

import ci.assurzen.entities.Compagnie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompagnieRepository extends JpaRepository<Compagnie, Long> {

    Optional<Compagnie> findByCodeCompagnie(String codeCompagnie);
}
