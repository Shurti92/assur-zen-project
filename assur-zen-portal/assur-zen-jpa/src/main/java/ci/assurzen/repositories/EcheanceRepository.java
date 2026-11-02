package ci.assurzen.repositories;

import ci.assurzen.entities.Echeance;
import ci.assurzen.entities.StatutEcheance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EcheanceRepository extends JpaRepository<Echeance, Long> {

    List<Echeance> findByContractIdOrderByNumeroEcheanceAsc(Long contractId);

    List<Echeance> findByStatutAndDateEcheanceBefore(StatutEcheance statut, LocalDate date);

    Optional<Echeance> findByPaiementId(Long paiementId);
}
