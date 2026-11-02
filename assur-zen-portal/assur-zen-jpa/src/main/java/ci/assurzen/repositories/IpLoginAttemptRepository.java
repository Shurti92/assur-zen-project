package ci.assurzen.repositories;

import ci.assurzen.entities.IpLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IpLoginAttemptRepository extends JpaRepository<IpLoginAttempt, Long> {

    Optional<IpLoginAttempt> findByIpAddress(String ipAddress);

    List<IpLoginAttempt> findByBlockedUntilAfterOrderByBlockedUntilDesc(Instant now);
}
