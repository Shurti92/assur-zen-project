package ci.assurzen.repositories;

import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionPartialPaymentRequestRepository extends JpaRepository<CommissionPartialPaymentRequest, Long> {

    List<CommissionPartialPaymentRequest> findByPartenaireIdOrderByRequestedAtDesc(Long partenaireId);

    long countByPartenaireIdAndRequestedAtBetween(Long partenaireId, Instant start, Instant end);

    List<CommissionPartialPaymentRequest> findByPartenaireIdAndStatusIn(Long partenaireId, List<PartialPaymentRequestStatus> statuses);

    /** Deduplication (double-clic / retry reseau) - audit de securite du 2026-08-23. */
    Optional<CommissionPartialPaymentRequest> findFirstByPartenaireIdAndRequestedAmountAndRequestedAtAfterOrderByRequestedAtDesc(
            Long partenaireId, Long requestedAmount, Instant after);

    /** Idempotence par cle fournie par le client (accord avec l'equipe frontend/securite du
     * 2026-08-23) - remplace la deduplication par fenetre de 10s ci-dessus quand le header
     * "Idempotency-Key" est fourni. */
    Optional<CommissionPartialPaymentRequest> findByPartenaireIdAndIdempotencyKey(Long partenaireId, String idempotencyKey);

    /** Verrou pessimiste - evite qu'une meme demande soit validee deux fois par deux appels
     * concurrents (meme faille de principe que le comptage quotidien, remontee par l'audit du
     * 2026-08-23). Requete NATIVE (pas @Lock + JPQL) : voir le commentaire de lockToutesPourPartenaire
     * ci-dessous pour le detail du bug de dialecte MySQL/MariaDB ("for update of p1_0" invalide sur
     * MariaDB) confirme en live le 2026-08-23. */
    @Query(value = "select * from t_assur_zen_commission_partial_payment_request where id = :id for update", nativeQuery = true)
    Optional<CommissionPartialPaymentRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * Verrouille TOUTES les lignes existantes de ce partenaire dans CETTE table (verrou de plage/
     * next-key InnoDB sur l'index partenaire_id sous REPEATABLE READ) - bloque, pour ce partenaire
     * precis, non seulement les UPDATE concurrents mais aussi tout INSERT concurrent qui matcherait
     * le meme partenaire_id (protection anti-phantom-read), tant que cette transaction n'a pas
     * commit/rollback. C'est la CIBLE CORRECTE du verrou pour ce cas d'usage - contrairement a un
     * verrou sur la ligne Partenaire (essaye initialement, insuffisant : verifie en live le
     * 2026-08-23, un verrou sur t_assur_zen_partenaire ne serialise QUE les acces concurrents a CETTE
     * table, pas les lectures/insertions dans t_assur_zen_commission_partial_payment_request qui sont
     * la vraie source de la race condition "2 demandes/jour" et du calcul du solde net). Le retour
     * (liste) n'est pas utilise par l'appelant, seul l'effet de verrouillage compte.
     */
    @Query(value = "select * from t_assur_zen_commission_partial_payment_request where partenaire_id = :partenaireId for update", nativeQuery = true)
    List<CommissionPartialPaymentRequest> lockToutesPourPartenaire(@Param("partenaireId") Long partenaireId);

    /** Avances (demandes payees) d'un partenaire, les plus anciennes d'abord - imputation sur les
     * paiements par contrat (2026-09-26). */
    List<CommissionPartialPaymentRequest> findByPartenaireIdAndStatusOrderByRequestedAtAsc(Long partenaireId,
                                                                                         PartialPaymentRequestStatus status);

    // ---- Demandes des commerciaux internes (2026-09-25) ----

    List<CommissionPartialPaymentRequest> findByCommercialIdOrderByRequestedAtDesc(Long commercialId);

    List<CommissionPartialPaymentRequest> findByCommercialIdAndStatusIn(Long commercialId, List<PartialPaymentRequestStatus> statuses);

    boolean existsByCommercialIdAndStatusIn(Long commercialId, List<PartialPaymentRequestStatus> statuses);

    Optional<CommissionPartialPaymentRequest> findByCommercialIdAndIdempotencyKey(Long commercialId, String idempotencyKey);

    Optional<CommissionPartialPaymentRequest> findFirstByCommercialIdAndRequestedAmountAndRequestedAtAfterOrderByRequestedAtDesc(
            Long commercialId, Long requestedAmount, Instant after);

    /** Meme role que lockToutesPourPartenaire (serialise les demandes concurrentes d'un meme
     * commercial avant le calcul du solde disponible), meme raison pour la requete native. */
    @Query(value = "select * from t_assur_zen_commission_partial_payment_request where commercial_id = :commercialId for update", nativeQuery = true)
    List<CommissionPartialPaymentRequest> lockToutesPourCommercial(@Param("commercialId") Long commercialId);

    /** Ecran de validation admin (partenaires et commerciaux) - status null = toutes. */
    @Query("""
            SELECT r FROM CommissionPartialPaymentRequest r
            LEFT JOIN FETCH r.partenaire
            LEFT JOIN FETCH r.commercial c
            LEFT JOIN FETCH c.profile
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.requestedAt DESC
            """)
    List<CommissionPartialPaymentRequest> findForValidation(@Param("status") PartialPaymentRequestStatus status);
}
