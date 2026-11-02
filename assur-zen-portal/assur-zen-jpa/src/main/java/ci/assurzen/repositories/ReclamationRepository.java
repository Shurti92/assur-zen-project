package ci.assurzen.repositories;

import ci.assurzen.entities.Reclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    Optional<Reclamation> findByNumero(String numero);

    List<Reclamation> findByInsuredId(Long insuredId);
}
