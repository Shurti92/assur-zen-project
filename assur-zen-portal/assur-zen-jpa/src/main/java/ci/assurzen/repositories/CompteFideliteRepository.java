package ci.assurzen.repositories;

import ci.assurzen.entities.CompteFidelite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompteFideliteRepository extends JpaRepository<CompteFidelite, Long> {

    Optional<CompteFidelite> findByInsuredId(Long insuredId);
}
