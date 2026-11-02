package ci.assurzen.repositories;

import ci.assurzen.entities.CampagneDestinataire;
import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.StatutEnvoiDestinataire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampagneDestinataireRepository extends JpaRepository<CampagneDestinataire, Long> {

    List<CampagneDestinataire> findByCampagneIdOrderByCreatedAtAsc(Long campagneId);

    long countByCampagneIdAndStatutEnvoi(Long campagneId, StatutEnvoiDestinataire statutEnvoi);

    boolean existsByCampagneIdAndInsuredIdAndCanal(Long campagneId, Long insuredId, CanalEnvoi canal);
}
