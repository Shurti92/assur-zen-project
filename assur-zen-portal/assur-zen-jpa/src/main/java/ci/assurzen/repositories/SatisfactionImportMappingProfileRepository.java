package ci.assurzen.repositories;

import ci.assurzen.entities.SatisfactionImportMappingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SatisfactionImportMappingProfileRepository extends JpaRepository<SatisfactionImportMappingProfile, Long> {

    Optional<SatisfactionImportMappingProfile> findByCode(String code);
}
