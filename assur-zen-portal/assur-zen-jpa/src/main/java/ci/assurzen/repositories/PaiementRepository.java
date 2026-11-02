package ci.assurzen.repositories;

import ci.assurzen.entities.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    List<Paiement> findByContractIdOrderByDatePaiementAsc(Long contractId);

    /** Encaissements (§9 tresorerie) - seule donnee d'encaissement existante dans le projet.
     * Paiement.montant est un Long (pas BigDecimal, incoherence historique de ce module anterieure a
     * ce lot - conversion faite cote service, pas "corrigee" ici, hors perimetre). datePaiement est un
     * LocalDate (pas Instant comme AuditableEntity), pas de conversion de fuseau necessaire ici. */
    @Query("SELECT COALESCE(SUM(p.montant), 0) FROM Paiement p WHERE p.datePaiement BETWEEN :debut AND :fin")
    long sumMontant(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
