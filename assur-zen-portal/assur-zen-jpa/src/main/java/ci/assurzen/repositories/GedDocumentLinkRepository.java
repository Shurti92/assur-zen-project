package ci.assurzen.repositories;

import ci.assurzen.entities.GedDocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GedDocumentLinkRepository extends JpaRepository<GedDocumentLink, Long> {

    List<GedDocumentLink> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    Optional<GedDocumentLink> findByIdAndDocumentId(Long id, Long documentId);

    List<GedDocumentLink> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
