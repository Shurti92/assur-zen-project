package ci.assurzen.repositories;

import ci.assurzen.entities.MobileNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileNotificationPreferenceRepository extends JpaRepository<MobileNotificationPreference, Long> {

    List<MobileNotificationPreference> findByUserId(Long userId);

    Optional<MobileNotificationPreference> findByUserIdAndCategory(Long userId, String category);
}
