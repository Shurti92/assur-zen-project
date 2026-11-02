package ci.assurzen.webpush.repository;

import ci.assurzen.webpush.entity.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpointHash(String endpointHash);

    List<WebPushSubscription> findByUserIdAndActiveTrue(Long userId);

    List<WebPushSubscription> findByUserIdInAndActiveTrue(Collection<Long> userIds);

    boolean existsByUserIdAndActiveTrue(Long userId);
}
