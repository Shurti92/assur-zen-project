package ci.assurzen.repositories;

import ci.assurzen.entities.UserActionTraitee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserActionTraiteeRepository extends JpaRepository<UserActionTraitee, Long> {

    List<UserActionTraitee> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndCle(Long userId, String cle);

    @Modifying
    long deleteByUserIdAndCle(Long userId, String cle);
}
