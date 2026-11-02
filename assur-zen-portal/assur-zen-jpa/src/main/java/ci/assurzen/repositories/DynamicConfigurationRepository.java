package ci.assurzen.repositories;

import ci.assurzen.entities.DynamicConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DynamicConfigurationRepository extends JpaRepository<DynamicConfiguration, Long> {

    Optional<DynamicConfiguration> findByConfigKey(String configKey);
}
