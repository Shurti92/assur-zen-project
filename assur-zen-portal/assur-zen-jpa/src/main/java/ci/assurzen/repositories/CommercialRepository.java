package ci.assurzen.repositories;

import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.StatutCommercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommercialRepository extends JpaRepository<Commercial, Long> {

    Optional<Commercial> findByProfileId(Long profileId);

    Optional<Commercial> findByProfile_UserId(Long userId);

    boolean existsByEmployeeCode(String employeeCode);

    List<Commercial> findByStatut(StatutCommercial statut);
}
