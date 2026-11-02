package ci.assurzen.repositories;

import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.repositories.projection.LibelleCount;
import ci.assurzen.repositories.projection.PeriodeCountMontant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeVignetteMotoRepository extends JpaRepository<DemandeVignetteMoto, Long> {

    List<DemandeVignetteMoto> findAllByOrderByCreatedAtDesc();

    Optional<DemandeVignetteMoto> findByReference(String reference);

    // ---------------------------------------------------------------- Dashboard KPI (module "KPI Dashboard",
    // §17 du contrat : agregation SQL, jamais findAll()+stream() pour ces volumes potentiellement importants.

    long countByCreatedAtBetween(Instant debut, Instant fin);

    long countByCreatedAtBetweenAndTypeDemande(Instant debut, Instant fin, TypeDemandeVignette typeDemande);

    long countByCreatedAtBetweenAndStatut(Instant debut, Instant fin, StatutDemandeVignetteMoto statut);

    long countByCreatedAtBetweenAndStatutIn(Instant debut, Instant fin, Collection<StatutDemandeVignetteMoto> statuts);

    @Query("SELECT COALESCE(SUM(d.montantVignette), 0) FROM DemandeVignetteMoto d WHERE d.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumMontantVignette(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(SUM(d.fraisOperation), 0) FROM DemandeVignetteMoto d WHERE d.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumFraisOperation(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(SUM(d.fraisLivraison), 0) FROM DemandeVignetteMoto d WHERE d.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumFraisLivraison(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(SUM(d.fraisAccessoires), 0) FROM DemandeVignetteMoto d WHERE d.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumFraisAccessoires(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(SUM(d.montantTotal), 0) FROM DemandeVignetteMoto d WHERE d.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumMontantTotal(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT d.typeDemande AS libelle, COUNT(d) AS total FROM DemandeVignetteMoto d " +
            "WHERE d.createdAt BETWEEN :debut AND :fin GROUP BY d.typeDemande")
    List<LibelleCount> repartitionParTypeDemande(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT d.categorieCylindreeAppliquee AS libelle, COUNT(d) AS total FROM DemandeVignetteMoto d " +
            "WHERE d.createdAt BETWEEN :debut AND :fin GROUP BY d.categorieCylindreeAppliquee")
    List<LibelleCount> repartitionParCylindree(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT d.categorieAgeAppliquee AS libelle, COUNT(d) AS total FROM DemandeVignetteMoto d " +
            "WHERE d.createdAt BETWEEN :debut AND :fin GROUP BY d.categorieAgeAppliquee")
    List<LibelleCount> repartitionParAgeMoto(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT d.statut AS libelle, COUNT(d) AS total FROM DemandeVignetteMoto d " +
            "WHERE d.createdAt BETWEEN :debut AND :fin GROUP BY d.statut")
    List<LibelleCount> repartitionParStatut(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT z.code AS libelle, COUNT(d) AS total FROM DemandeVignetteMoto d JOIN d.fraisLivraisonZone z " +
            "WHERE d.createdAt BETWEEN :debut AND :fin GROUP BY z.code")
    List<LibelleCount> repartitionParZoneLivraison(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Granularite jour - adaptee a une periode courte (§6 : ~7 jours -> JOUR). Requete SQL native
     * (DATE_FORMAT specifique MariaDB), meme choix que FundOutflowRepository.findByIdForUpdate pour les
     * cas ou JPQL ne suffit pas. */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS periode, COUNT(*) AS total, COALESCE(SUM(montant_total), 0) AS montant " +
            "FROM t_assur_zen_demande_vignette_moto WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParJour(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Granularite semaine ISO (%x-%v, annee-semaine ISO 8601 - coherent quelle que soit l'annee). */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%x-S%v') AS periode, COUNT(*) AS total, COALESCE(SUM(montant_total), 0) AS montant " +
            "FROM t_assur_zen_demande_vignette_moto WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParSemaine(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Granularite mois - adaptee a une periode longue (§6 : plusieurs mois -> MOIS). */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS periode, COUNT(*) AS total, COALESCE(SUM(montant_total), 0) AS montant " +
            "FROM t_assur_zen_demande_vignette_moto WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParMois(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
