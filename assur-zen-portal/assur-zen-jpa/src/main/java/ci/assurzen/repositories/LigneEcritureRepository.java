package ci.assurzen.repositories;

import ci.assurzen.entities.LigneEcriture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneEcritureRepository extends JpaRepository<LigneEcriture, Long> {

    /**
     * Tri chronologique stable : plusieurs ecritures peuvent partager la meme dateEcriture (simple LocalDate),
     * donc l'id de l'ecriture (puis de la ligne) sert de critere de depart pour departager et garantir
     * un ordre reproductible pour le calcul du solde progressif.
     */
    @Query("SELECT l FROM LigneEcriture l WHERE l.compte.id = :compteId "
            + "ORDER BY l.ecriture.dateEcriture ASC, l.ecriture.id ASC, l.id ASC")
    List<LigneEcriture> findByCompteIdOrderChronologique(@Param("compteId") Long compteId);
}
