package ci.assurzen.repositories;

import ci.assurzen.entities.MobileNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MobileNotificationRepository extends JpaRepository<MobileNotification, Long> {

    Page<MobileNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<MobileNotification> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    @Modifying
    @Query("UPDATE MobileNotification n SET n.readAt = :maintenant WHERE n.userId = :userId AND n.readAt IS NULL")
    int toutMarquerLu(@Param("userId") Long userId, @Param("maintenant") Instant maintenant);

    @Modifying
    @Query("DELETE FROM MobileNotification n WHERE n.createdAt < :limite")
    int supprimerAnterieures(@Param("limite") Instant limite);
}
