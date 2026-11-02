package ci.assurzen.repositories;

import ci.assurzen.entities.CommunicationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, Long> {

    Optional<CommunicationTemplate> findByCode(String code);
}
