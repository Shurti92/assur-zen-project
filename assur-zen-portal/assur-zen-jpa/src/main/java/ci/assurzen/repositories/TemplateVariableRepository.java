package ci.assurzen.repositories;

import ci.assurzen.entities.TemplateVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, Long> {

    Optional<TemplateVariable> findByCode(String code);

    List<TemplateVariable> findByCodeIn(List<String> codes);
}
