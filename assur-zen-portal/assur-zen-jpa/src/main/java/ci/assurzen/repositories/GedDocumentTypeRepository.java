package ci.assurzen.repositories;

import ci.assurzen.entities.GedDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GedDocumentTypeRepository extends JpaRepository<GedDocumentType, Long> {

    Optional<GedDocumentType> findByCodeIgnoreCase(String code);

    List<GedDocumentType> findByActifTrueOrderByOrdreAsc();
}
