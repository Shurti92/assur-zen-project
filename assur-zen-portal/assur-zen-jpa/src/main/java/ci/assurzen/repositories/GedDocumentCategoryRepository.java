package ci.assurzen.repositories;

import ci.assurzen.entities.GedDocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GedDocumentCategoryRepository extends JpaRepository<GedDocumentCategory, Long> {

    Optional<GedDocumentCategory> findByCodeIgnoreCase(String code);

    List<GedDocumentCategory> findByActifTrueOrderByOrdreAsc();
}
