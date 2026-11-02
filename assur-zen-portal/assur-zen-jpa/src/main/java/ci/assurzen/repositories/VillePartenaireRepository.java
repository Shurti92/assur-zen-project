package ci.assurzen.repositories;

import ci.assurzen.entities.VillePartenaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VillePartenaireRepository extends JpaRepository<VillePartenaire, Long> {
}
