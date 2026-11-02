package ci.assurzen.repositories;

import ci.assurzen.entities.Sinistre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SinistreRepository extends JpaRepository<Sinistre, Long> {

    Optional<Sinistre> findByNumero(String numero);

    List<Sinistre> findByContractIdOrderByCreatedAtDesc(Long contractId);

    List<Sinistre> findByInsuredIdOrderByCreatedAtDesc(Long insuredId);

    Optional<Sinistre> findByIdAndContract_SalesRep_Id(Long id, Long salesRepId);
}
