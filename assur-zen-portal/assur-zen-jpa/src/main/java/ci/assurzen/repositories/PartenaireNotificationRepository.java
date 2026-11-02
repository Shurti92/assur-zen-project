package ci.assurzen.repositories;

import ci.assurzen.entities.PartenaireNotification;
import ci.assurzen.entities.PartenaireNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PartenaireNotificationRepository extends JpaRepository<PartenaireNotification, Long> {

    Page<PartenaireNotification> findByPartenaireId(Long partenaireId, Pageable pageable);

    Page<PartenaireNotification> findByPartenaireIdAndLuFalse(Long partenaireId, Pageable pageable);

    long countByPartenaireIdAndLuFalse(Long partenaireId);

    Optional<PartenaireNotification> findByIdAndPartenaireId(Long id, Long partenaireId);

    @Modifying
    @Query("UPDATE PartenaireNotification n SET n.lu = true, n.luAt = :luAt WHERE n.partenaire.id = :partenaireId AND n.lu = false")
    int marquerToutesLues(@Param("partenaireId") Long partenaireId, @Param("luAt") Instant luAt);

    // ---- Centre de notifications de l'utilisateur connecte (2026-10-03) : destinataire = partenaire
    // (partenaireId) ou utilisateur interne (userId), notifications supprimees par lui exclues ----

    /** Anti-doublon des rappels planifies (2026-10-04). */
    boolean existsByTypeAndObjetTypeAndObjetId(PartenaireNotificationType type, String objetType, Long objetId);

    boolean existsByTypeAndObjetTypeAndObjetIdAndCreatedAtGreaterThanEqual(PartenaireNotificationType type, String objetType,
                                                                          Long objetId, Instant depuis);

    String DESTINATAIRE = "((:partenaireId IS NOT NULL AND n.partenaire.id = :partenaireId)"
            + " OR (:partenaireId IS NULL AND n.userId = :userId AND n.partenaire IS NULL))"
            + " AND (n.supprimee IS NULL OR n.supprimee = false)";

    @Query("SELECT n FROM PartenaireNotification n WHERE " + DESTINATAIRE + " AND (:nonLues = false OR n.lu = false)")
    Page<PartenaireNotification> findVisibles(@Param("partenaireId") Long partenaireId, @Param("userId") Long userId,
                                             @Param("nonLues") boolean nonLues, Pageable pageable);

    @Query("SELECT COUNT(n) FROM PartenaireNotification n WHERE " + DESTINATAIRE + " AND n.lu = false")
    long countVisiblesNonLues(@Param("partenaireId") Long partenaireId, @Param("userId") Long userId);

    @Query("SELECT n FROM PartenaireNotification n WHERE n.id = :id AND " + DESTINATAIRE)
    Optional<PartenaireNotification> findVisible(@Param("id") Long id, @Param("partenaireId") Long partenaireId,
                                                 @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE PartenaireNotification n SET n.lu = true, n.luAt = :luAt WHERE " + DESTINATAIRE + " AND n.lu = false")
    int marquerToutesVisiblesLues(@Param("partenaireId") Long partenaireId, @Param("userId") Long userId,
                                  @Param("luAt") Instant luAt);
}
