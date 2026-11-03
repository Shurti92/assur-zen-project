package ci.assurzen.repositories;

import ci.assurzen.entities.MobileDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileDeviceRepository extends JpaRepository<MobileDevice, Long> {

    Optional<MobileDevice> findByToken(String token);

    Optional<MobileDevice> findByIdAndUserId(Long id, Long userId);

    List<MobileDevice> findByUserIdAndActiveTrue(Long userId);
}
