package ci.assurzen.repositories;

import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.repositories.projection.LibelleCount;
import ci.assurzen.repositories.projection.LibelleCountMontant;
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
public interface FundOutflowRepository extends JpaRepository<FundOutflow, Long> {

    Optional<FundOutflow> findByReference(String reference);

    /** Verrou pessimiste sur la ligne fund_outflow elle-meme, requete NATIVE (pas @Lock + JPQL) -
     * meme raison que CommissionPartialPaymentRequestRepository.findByIdForUpdate : Hibernate genere
     * "for update of <alias>" pour un @Lock JPQL, syntaxe rejetee par le parseur MariaDB. */
    @Query(value = "select * from t_assur_zen_fund_outflows where id = :id for update", nativeQuery = true)
    Optional<FundOutflow> findByIdForUpdate(@Param("id") Long id);

    // ---------------------------------------------------------------- Dashboard KPI (module "KPI Dashboard")

    long countByCreatedAtBetween(Instant debut, Instant fin);

    long countByCreatedAtBetweenAndStatusIn(Instant debut, Instant fin, Collection<FundOutflowStatus> statuts);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FundOutflow f WHERE f.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumAmount(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FundOutflow f WHERE f.createdAt BETWEEN :debut AND :fin AND f.status IN :statuts")
    BigDecimal sumAmountByStatusIn(@Param("debut") Instant debut, @Param("fin") Instant fin, @Param("statuts") Collection<FundOutflowStatus> statuts);

    @Query("SELECT COALESCE(AVG(f.amount), 0) FROM FundOutflow f WHERE f.createdAt BETWEEN :debut AND :fin")
    BigDecimal avgAmount(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT MAX(f.amount) FROM FundOutflow f WHERE f.createdAt BETWEEN :debut AND :fin")
    BigDecimal maxAmount(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT f.outflowType.code AS libelle, COUNT(f) AS total, COALESCE(SUM(f.amount), 0) AS montant FROM FundOutflow f " +
            "WHERE f.createdAt BETWEEN :debut AND :fin GROUP BY f.outflowType.code")
    List<LibelleCountMontant> repartitionParType(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT COALESCE(f.beneficiaryLabel, f.beneficiaryType, 'INCONNU') AS libelle, COUNT(f) AS total, COALESCE(SUM(f.amount), 0) AS montant " +
            "FROM FundOutflow f WHERE f.createdAt BETWEEN :debut AND :fin GROUP BY f.beneficiaryLabel, f.beneficiaryType")
    List<LibelleCountMontant> repartitionParBeneficiaire(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("SELECT f.status AS libelle, COUNT(f) AS total, COALESCE(SUM(f.amount), 0) AS montant FROM FundOutflow f " +
            "WHERE f.createdAt BETWEEN :debut AND :fin GROUP BY f.status")
    List<LibelleCountMontant> repartitionParStatut(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Delai moyen (jours) entre soumission et decaissement effectif, sur les sorties reellement
     * decaissees dans la periode - seules dates fiables pour un "delai de traitement" (§7). */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(HOUR, submitted_at, disbursed_at)) / 24.0 FROM t_assur_zen_fund_outflows " +
            "WHERE disbursed_at IS NOT NULL AND submitted_at IS NOT NULL AND created_at BETWEEN :debut AND :fin", nativeQuery = true)
    Double delaiMoyenTraitementJours(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS periode, COUNT(*) AS total, COALESCE(SUM(amount), 0) AS montant " +
            "FROM t_assur_zen_fund_outflows WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParJour(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%x-S%v') AS periode, COUNT(*) AS total, COALESCE(SUM(amount), 0) AS montant " +
            "FROM t_assur_zen_fund_outflows WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParSemaine(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS periode, COUNT(*) AS total, COALESCE(SUM(amount), 0) AS montant " +
            "FROM t_assur_zen_fund_outflows WHERE created_at BETWEEN :debut AND :fin " +
            "GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCountMontant> evolutionParMois(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
