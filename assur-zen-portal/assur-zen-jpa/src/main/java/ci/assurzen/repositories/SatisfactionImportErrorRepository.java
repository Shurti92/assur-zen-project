package ci.assurzen.repositories;

import ci.assurzen.entities.SatisfactionImportError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SatisfactionImportErrorRepository extends JpaRepository<SatisfactionImportError, Long> {

    List<SatisfactionImportError> findBySatisfactionImportIdOrderByLigneNumeroAsc(Long importId);

    void deleteBySatisfactionImportId(Long importId);
}
