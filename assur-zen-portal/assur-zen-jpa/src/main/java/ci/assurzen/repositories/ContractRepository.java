package ci.assurzen.repositories;

import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.repositories.projection.PeriodeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findBySalesRepIdOrderByCreatedAtDesc(Long salesRepId);

    List<Contract> findByPartenaireIdOrderByCreatedAtDesc(Long partenaireId);

    Optional<Contract> findByIdAndSalesRepId(Long id, Long salesRepId);

    Optional<Contract> findByPolicyNumber(String policyNumber);

    long countByCreatedAtBetween(Instant start, Instant end);

    List<Contract> findByInsuredIdOrderByCreatedAtDesc(Long insuredId);

    /** Garde-fou IDOR (audit securite 2026-09-12) : un assure n'est accessible a un non-admin que s'il
     * a au moins un contrat ou il est salesRep sur cet assure - meme principe que
     * ContractsService.getContractScoped(), applique a InsuredService. */
    boolean existsByInsuredIdAndSalesRepId(Long insuredId, Long salesRepId);

    List<Contract> findByStatusAndPartenaireIsNotNull(ContractStatus status);

    List<Contract> findByStatusAndPartenaireId(ContractStatus status, Long partenaireId);

    /** Rappels automatiques de l'Espace Partenaire (2026-10-04). */
    List<Contract> findByStatusAndPartenaireIsNotNullAndDateEcheanceIn(ContractStatus status, java.util.Collection<java.time.LocalDate> dates);

    List<Contract> findByStatusAndPrimeReversementStatusAndPartenaireIsNotNull(ContractStatus status,
                                                                             ci.assurzen.entities.StatutReversementPrime statut);

    List<Contract> findByStatusAndSalesRepId(ContractStatus status, Long salesRepId);

    List<Contract> findByStatusAndPartenaireIdAndCommissionStatus(ContractStatus status, Long partenaireId, CommissionStatus commissionStatus);

    /** Contrats eligibles a une commission commerciale (vente directe, sans partenaire) - meme
     * convention que findByStatusAndPartenaireIsNotNull pour le cote partenaire. */
    List<Contract> findByStatusAndPartenaireIsNull(ContractStatus status);

    List<Contract> findByStatusAndPartenaireIsNullAndSalesRepId(ContractStatus status, Long salesRepId);

    List<Contract> findByStatusAndPartenaireIsNullAndSalesRepIdAndCommercialCommissionStatus(
            ContractStatus status, Long salesRepId, CommissionStatus commercialCommissionStatus);

    List<Contract> findByStatusAndExerciceComptableId(ContractStatus status, Long exerciceComptableId);

    Optional<Contract> findByContratPrecedentId(Long contratPrecedentId);

    List<Contract> findByVehiculeIdInOrderByCreatedAtDesc(List<Long> vehiculeIds);

    boolean existsByVehiculeId(Long vehiculeId);

    // ---------------------------------------------------------------- Dashboard KPI - Assures (module "KPI
    // Dashboard"). Requetes SQL natives (sous-requete + HAVING, non exprimable proprement en derivation
    // Spring Data) plutot que findAll()+stream(), cf. §17 du contrat.

    /** Assure actif = au moins un contrat SIGNE cree au plus tard a la date de reference, dont
     * l'echeance n'etait pas encore depassee a cette meme date (decision produit du 2026-09-11,
     * coherent avec DashboardBackOfficeKpis.CycleDeVie). Parametree par une date de reference (plutot
     * qu'un "maintenant" fige) pour permettre une vraie reconstruction "a la periode precedente" -
     * sinon comparer un etat instantane a lui-meme donnerait toujours 0% de variation. Appelee avec
     * Instant.now()/LocalDate.now() pour la valeur courante, avec la fin de periode precedente sinon. */
    @Query(value = "SELECT COUNT(DISTINCT insured_id) FROM t_assur_zen_contracts " +
            "WHERE status = 'signed' AND created_at <= :borneInstant AND (date_echeance IS NULL OR date_echeance >= :borneDate)", nativeQuery = true)
    long countAssuresActifsAvant(@Param("borneInstant") Instant borneInstant, @Param("borneDate") LocalDate borneDate);

    /** §10 : 2+ contrats SIGNES crees au plus tard a la date de reference, meme produit ou non. */
    @Query(value = "SELECT COUNT(*) FROM (SELECT insured_id FROM t_assur_zen_contracts " +
            "WHERE status = 'signed' AND created_at <= :borneInstant GROUP BY insured_id HAVING COUNT(*) >= 2) x", nativeQuery = true)
    long countAssuresPlusieursContratsAvant(@Param("borneInstant") Instant borneInstant);

    /** §11 : 2+ contrats SIGNES crees au plus tard a la date de reference, sur des produits DISTINCTS
     * (decision produit du 2026-09-11 - aucune definition preexistante dans le code, verifie avant de
     * choisir celle-ci). */
    @Query(value = "SELECT COUNT(*) FROM (SELECT insured_id FROM t_assur_zen_contracts " +
            "WHERE status = 'signed' AND created_at <= :borneInstant GROUP BY insured_id HAVING COUNT(DISTINCT product_id) >= 2) x", nativeQuery = true)
    long countAssuresMultiEquipesAvant(@Param("borneInstant") Instant borneInstant);

    @Query(value = "SELECT COALESCE(AVG(x.nb), 0) FROM (SELECT insured_id, COUNT(*) AS nb FROM t_assur_zen_contracts " +
            "WHERE status = 'signed' AND created_at <= :borneInstant GROUP BY insured_id) x", nativeQuery = true)
    Double moyenneContratsParAssureActifAvant(@Param("borneInstant") Instant borneInstant);

    /** "Nouveaux assures" = assures dont le PREMIER contrat (tous statuts confondus, insured n'a pas de
     * createdAt propre) a ete cree sur la periode - seule donnee temporelle disponible, cf. javadoc
     * DashboardAssureResponse.Kpis.nouveauxAssures. */
    @Query(value = "SELECT COUNT(*) FROM (SELECT insured_id, MIN(created_at) AS premiere_date " +
            "FROM t_assur_zen_contracts GROUP BY insured_id) x WHERE x.premiere_date BETWEEN :debut AND :fin", nativeQuery = true)
    long countNouveauxAssures(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Assures "connus" (premier contrat) au plus tard a une date de reference - sert de base cumulative
     * pour reconstruire un total glissant dans l'evolution temporelle (§13), sans meler ce proxy au
     * comptage brut de t_assur_zen_insured (les deux ne mesurent pas exactement la meme chose). */
    @Query(value = "SELECT COUNT(*) FROM (SELECT insured_id, MIN(created_at) AS premiere_date " +
            "FROM t_assur_zen_contracts GROUP BY insured_id) x WHERE x.premiere_date <= :borneInstant", nativeQuery = true)
    long countAssuresConnusAvant(@Param("borneInstant") Instant borneInstant);

    @Query(value = "SELECT DATE_FORMAT(x.premiere_date, '%Y-%m-%d') AS periode, COUNT(*) AS total FROM " +
            "(SELECT insured_id, MIN(created_at) AS premiere_date FROM t_assur_zen_contracts GROUP BY insured_id) x " +
            "WHERE x.premiere_date BETWEEN :debut AND :fin GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCount> evolutionNouveauxAssuresParJour(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query(value = "SELECT DATE_FORMAT(x.premiere_date, '%x-S%v') AS periode, COUNT(*) AS total FROM " +
            "(SELECT insured_id, MIN(created_at) AS premiere_date FROM t_assur_zen_contracts GROUP BY insured_id) x " +
            "WHERE x.premiere_date BETWEEN :debut AND :fin GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCount> evolutionNouveauxAssuresParSemaine(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query(value = "SELECT DATE_FORMAT(x.premiere_date, '%Y-%m') AS periode, COUNT(*) AS total FROM " +
            "(SELECT insured_id, MIN(created_at) AS premiere_date FROM t_assur_zen_contracts GROUP BY insured_id) x " +
            "WHERE x.premiere_date BETWEEN :debut AND :fin GROUP BY periode ORDER BY periode", nativeQuery = true)
    List<PeriodeCount> evolutionNouveauxAssuresParMois(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
