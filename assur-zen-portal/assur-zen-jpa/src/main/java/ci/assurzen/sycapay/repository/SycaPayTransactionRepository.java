package ci.assurzen.sycapay.repository;

import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface SycaPayTransactionRepository extends JpaRepository<SycaPayTransaction, Long> {

    Optional<SycaPayTransaction> findByContractId(Long contractId);

    /** Virement d'une demande de paiement (avance / demande de commercial), 2026-09-26. */
    Optional<SycaPayTransaction> findByPartialPaymentRequestId(Long partialPaymentRequestId);

    /** Liste de validation des demandes : les virements de toute une page en une requete. */
    java.util.List<SycaPayTransaction> findByPartialPaymentRequestIdIn(Collection<Long> partialPaymentRequestIds);

    /** Virement du bonus mensuel d'un commercial (CommercialPerformance), 2026-10-02. */
    Optional<SycaPayTransaction> findByCommercialPerformanceId(Long commercialPerformanceId);

    Optional<SycaPayTransaction> findByCommercialBenefitPaymentId(Long commercialBenefitPaymentId);

    java.util.List<SycaPayTransaction> findByCommercialBenefitPaymentIdIn(Collection<Long> commercialBenefitPaymentIds);

    java.util.List<SycaPayTransaction> findByCommercialPerformanceIdIn(Collection<Long> commercialPerformanceIds);

    Optional<SycaPayTransaction> findByTransactionReference(String transactionReference);

    boolean existsByContractIdAndStatusIn(Long contractId, Collection<SycaPayTransactionStatus> statuses);

    /** Utilise par la reconciliation manuelle/planifiee (section 11) : transactions dont le resultat
     * reel du virement Wave n'est toujours pas confirme. */
    java.util.List<SycaPayTransaction> findByStatusIn(Collection<SycaPayTransactionStatus> statuses);

    /** Utilise par le tableau de bord de monitoring (section 15 - "Paiements du jour"). */
    java.util.List<SycaPayTransaction> findByRequestDateGreaterThanEqual(Instant startOfDay);

    @Query("""
            SELECT t FROM SycaPayTransaction t
            WHERE (:reference IS NULL OR t.transactionReference = :reference)
              AND (:commercialId IS NULL OR t.commercialId = :commercialId)
              AND (:partenaireId IS NULL OR t.partenaireId = :partenaireId)
              AND (:contractId IS NULL OR t.contractId = :contractId)
              AND (:beneficiaryType IS NULL
                   OR COALESCE(t.beneficiaryType, ci.assurzen.sycapay.entity.SycaPayBeneficiaryType.COMMERCIAL) = :beneficiaryType)
              AND (:status IS NULL OR t.status = :status)
              AND (:dateStart IS NULL OR t.requestDate >= :dateStart)
              AND (:dateEnd IS NULL OR t.requestDate <= :dateEnd)
            """)
    Page<SycaPayTransaction> search(
            @Param("reference") String reference,
            @Param("commercialId") Long commercialId,
            @Param("partenaireId") Long partenaireId,
            @Param("contractId") Long contractId,
            @Param("beneficiaryType") SycaPayBeneficiaryType beneficiaryType,
            @Param("status") SycaPayTransactionStatus status,
            @Param("dateStart") Instant dateStart,
            @Param("dateEnd") Instant dateEnd,
            Pageable pageable
    );

    /** beneficiaryType NULL en base = ligne anterieure au 2026-09-23, donc COMMERCIAL (COALESCE). */

    /** Repartition par statut avec les MEMES filtres que search() (sauf status) - pastilles de filtre
     * de l'ecran Transactions du frontend (2026-09-23). Une ligne [status, count] par statut present. */
    @Query("""
            SELECT t.status, COUNT(t) FROM SycaPayTransaction t
            WHERE (:reference IS NULL OR t.transactionReference = :reference)
              AND (:commercialId IS NULL OR t.commercialId = :commercialId)
              AND (:partenaireId IS NULL OR t.partenaireId = :partenaireId)
              AND (:contractId IS NULL OR t.contractId = :contractId)
              AND (:beneficiaryType IS NULL
                   OR COALESCE(t.beneficiaryType, ci.assurzen.sycapay.entity.SycaPayBeneficiaryType.COMMERCIAL) = :beneficiaryType)
              AND (:dateStart IS NULL OR t.requestDate >= :dateStart)
              AND (:dateEnd IS NULL OR t.requestDate <= :dateEnd)
            GROUP BY t.status
            """)
    java.util.List<Object[]> countByStatus(
            @Param("reference") String reference,
            @Param("commercialId") Long commercialId,
            @Param("partenaireId") Long partenaireId,
            @Param("contractId") Long contractId,
            @Param("beneficiaryType") SycaPayBeneficiaryType beneficiaryType,
            @Param("dateStart") Instant dateStart,
            @Param("dateEnd") Instant dateEnd
    );
}
