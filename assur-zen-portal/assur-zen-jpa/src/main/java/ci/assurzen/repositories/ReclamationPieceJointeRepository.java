package ci.assurzen.repositories;

import ci.assurzen.entities.ReclamationPieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReclamationPieceJointeRepository extends JpaRepository<ReclamationPieceJointe, Long> {

    List<ReclamationPieceJointe> findByReclamationIdOrderByCreatedAtAsc(Long reclamationId);

    Optional<ReclamationPieceJointe> findByIdAndReclamationId(Long id, Long reclamationId);

    boolean existsByReclamationId(Long reclamationId);
}
