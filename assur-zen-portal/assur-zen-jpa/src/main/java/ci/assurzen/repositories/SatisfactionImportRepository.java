package ci.assurzen.repositories;

import ci.assurzen.entities.SatisfactionImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SatisfactionImportRepository extends JpaRepository<SatisfactionImport, Long> {

    Optional<SatisfactionImport> findByCode(String code);
}
