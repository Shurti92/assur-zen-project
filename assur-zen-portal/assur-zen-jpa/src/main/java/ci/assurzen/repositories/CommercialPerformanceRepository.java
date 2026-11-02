package ci.assurzen.repositories;

import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.StatutCommercialPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommercialPerformanceRepository extends JpaRepository<CommercialPerformance, Long> {

    Optional<CommercialPerformance> findByCommercialIdAndMoisComptableId(Long commercialId, Long moisComptableId);

    List<CommercialPerformance> findByCommercialIdOrderByMoisComptable_NumeroDesc(Long commercialId);

    List<CommercialPerformance> findByMoisComptableId(Long moisComptableId);

    List<CommercialPerformance> findByStatut(StatutCommercialPerformance statut);

    /** Historique des paiements de bonus (2026-10-02) : bonus payes (SycaPay ou hors application) et
     * bonus dont un virement a ete tente (en cours, echoue...), filtrable par commercial. */
    @Query("""
            SELECT p FROM CommercialPerformance p
            WHERE (p.paidAt IS NOT NULL OR p.sycapayTransactionId IS NOT NULL)
              AND (:commercialId IS NULL OR p.commercial.id = :commercialId)
            ORDER BY COALESCE(p.paidAt, p.updatedAt) DESC
            """)
    List<CommercialPerformance> findBonusPaymentHistory(@Param("commercialId") Long commercialId);
}
