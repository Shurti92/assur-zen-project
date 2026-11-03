package ci.assurzen.repositories;

import ci.assurzen.entities.MobileFaq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MobileFaqRepository extends JpaRepository<MobileFaq, Long> {

    List<MobileFaq> findByVisibleTrueOrderByDisplayOrderAscIdAsc();

    List<MobileFaq> findAllByOrderByDisplayOrderAscIdAsc();
}
