package ci.assurzen.repositories;

import ci.assurzen.entities.ReclamationCommentaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationCommentaireRepository extends JpaRepository<ReclamationCommentaire, Long> {

    List<ReclamationCommentaire> findByReclamationIdOrderByCreatedAtAsc(Long reclamationId);

    boolean existsByReclamationId(Long reclamationId);
}
