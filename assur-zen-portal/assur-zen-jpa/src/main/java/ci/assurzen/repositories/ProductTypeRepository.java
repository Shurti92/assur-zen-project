package ci.assurzen.repositories;

import ci.assurzen.entities.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    Optional<ProductType> findByCodeIgnoreCase(String code);

    Optional<ProductType> findByNomIgnoreCase(String nom);

    List<ProductType> findByActifTrueOrderByOrdreAsc();
}
