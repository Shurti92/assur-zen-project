package ci.assurzen.repositories;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.TarifVignetteMoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TarifVignetteMotoRepository extends JpaRepository<TarifVignetteMoto, Long> {

    List<TarifVignetteMoto> findAllByOrderByCreatedAtDesc();

    /** Resolution du/des tarif(s) actif(s) couvrant une date donnee pour un couple (age, cylindree) -
     * doit renvoyer au plus une ligne si les tarifs actifs ne se chevauchent pas (garde-fou applique a
     * la creation/activation cote service, cf. TarifVignetteMotoService.validerAbsenceChevauchement()).
     * Plus d'une ligne en resultat signale une donnee de configuration incoherente. */
    @Query("""
            SELECT t FROM TarifVignetteMoto t
            WHERE t.categorieAge = :categorieAge
              AND t.categorieCylindree = :categorieCylindree
              AND t.actif = true
              AND t.dateDebutValidite <= :dateReference
              AND (t.dateFinValidite IS NULL OR t.dateFinValidite >= :dateReference)
            """)
    List<TarifVignetteMoto> findTarifsApplicables(
            @Param("categorieAge") CategorieAgeMoto categorieAge,
            @Param("categorieCylindree") CategorieCylindree categorieCylindree,
            @Param("dateReference") LocalDate dateReference);

    /** Tous les tarifs actifs pour un couple (age, cylindree), toutes plages de validite confondues -
     * la detection de chevauchement de plages (arithmetique sur LocalDate) est volontairement faite
     * cote service (TarifVignetteMotoService), pas ici, pour rester independante du dialecte SQL. */
    List<TarifVignetteMoto> findByCategorieAgeAndCategorieCylindreeAndActifTrue(
            CategorieAgeMoto categorieAge, CategorieCylindree categorieCylindree);
}
