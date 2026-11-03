package ci.assurzen.repositories;

import ci.assurzen.entities.ClientAccountInsured;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientAccountInsuredRepository extends JpaRepository<ClientAccountInsured, Long> {

    List<ClientAccountInsured> findByUserId(Long userId);

    boolean existsByUserIdAndInsuredId(Long userId, Long insuredId);

    void deleteByUserIdAndInsuredId(Long userId, Long insuredId);

    void deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
