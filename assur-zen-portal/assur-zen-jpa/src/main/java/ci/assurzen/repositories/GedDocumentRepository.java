package ci.assurzen.repositories;

import ci.assurzen.entities.GedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GedDocumentRepository extends JpaRepository<GedDocument, Long> {

    Optional<GedDocument> findFirstByChecksumSha256OrderByCreatedAtAsc(String checksumSha256);
}
