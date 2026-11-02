package ci.assurzen.repositories;

import ci.assurzen.entities.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {

    Optional<TemplateCategory> findByCode(String code);
}
