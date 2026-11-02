package ci.assurzen.repositories;

import ci.assurzen.entities.CommercialBenefitPayment;
import ci.assurzen.entities.TypeAvantageCommercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommercialBenefitPaymentRepository extends JpaRepository<CommercialBenefitPayment, Long> {

    Optional<CommercialBenefitPayment> findByCommercialIdAndMoisComptableIdAndType(Long commercialId, Long moisComptableId,
                                                                                   TypeAvantageCommercial type);

    List<CommercialBenefitPayment> findByMoisComptableId(Long moisComptableId);

    /** Historique : avantages payes ou dont un virement a ete tente, filtrable par commercial et par type. */
    @Query("""
            SELECT p FROM CommercialBenefitPayment p
            WHERE (p.paidAt IS NOT NULL OR p.sycapayTransactionId IS NOT NULL)
              AND (:commercialId IS NULL OR p.commercial.id = :commercialId)
              AND (:type IS NULL OR p.type = :type)
            ORDER BY COALESCE(p.paidAt, p.updatedAt) DESC
            """)
    List<CommercialBenefitPayment> findHistory(@Param("commercialId") Long commercialId,
                                               @Param("type") TypeAvantageCommercial type);
}
