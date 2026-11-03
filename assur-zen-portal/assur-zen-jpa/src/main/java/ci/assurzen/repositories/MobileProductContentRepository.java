package ci.assurzen.repositories;

import ci.assurzen.entities.MobileProductContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MobileProductContentRepository extends JpaRepository<MobileProductContent, Long> {

    Optional<MobileProductContent> findByProductId(Long productId);

    List<MobileProductContent> findByProductIdIn(Collection<Long> productIds);
}
