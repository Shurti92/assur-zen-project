package ci.assurzen.repositories;

import ci.assurzen.entities.Langue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LangueRepository extends JpaRepository<Langue, Long> {

    Optional<Langue> findByCode(String code);
}
