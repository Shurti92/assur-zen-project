package ci.assurzen.repositories;

import ci.assurzen.entities.GedDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GedDocumentVersionRepository extends JpaRepository<GedDocumentVersion, Long> {

    List<GedDocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<GedDocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<GedDocumentVersion> findByIdAndDocumentId(Long id, Long documentId);
}
