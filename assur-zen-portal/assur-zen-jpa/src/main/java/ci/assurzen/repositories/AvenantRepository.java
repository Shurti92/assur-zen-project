package ci.assurzen.repositories;

import ci.assurzen.entities.Avenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvenantRepository extends JpaRepository<Avenant, Long> {

    List<Avenant> findByContractIdOrderByNumeroAsc(Long contractId);

    long countByContractId(Long contractId);

    List<Avenant> findByContract_Partenaire_IdOrderByCreatedAtDesc(Long partenaireId);

    long countByContract_IdIn(List<Long> contractIds);
}
