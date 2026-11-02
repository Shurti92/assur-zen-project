package ci.assurzen.repositories;

import ci.assurzen.entities.PaiementPieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaiementPieceJointeRepository extends JpaRepository<PaiementPieceJointe, Long> {

    List<PaiementPieceJointe> findByPaiementIdOrderByCreatedAtAsc(Long paiementId);

    Optional<PaiementPieceJointe> findByIdAndPaiementId(Long id, Long paiementId);
}
