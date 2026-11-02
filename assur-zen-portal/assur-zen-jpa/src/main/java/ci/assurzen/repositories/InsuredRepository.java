package ci.assurzen.repositories;

import ci.assurzen.entities.Insured;
import ci.assurzen.repositories.projection.LibelleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsuredRepository extends JpaRepository<Insured, Long> {

    Optional<Insured> findByClientPhone(String clientPhone);

    // ---------------------------------------------------------------- Dashboard KPI (module "KPI Dashboard")

    @Query("SELECT i.clientCity AS libelle, COUNT(i) AS total FROM Insured i WHERE i.clientCity IS NOT NULL GROUP BY i.clientCity")
    List<LibelleCount> repartitionParVille();

    @Query("SELECT i.typeClient AS libelle, COUNT(i) AS total FROM Insured i WHERE i.typeClient IS NOT NULL GROUP BY i.typeClient")
    List<LibelleCount> repartitionParTypeClient();
}
