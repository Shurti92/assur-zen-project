package ci.assurzen.services;

import ci.assurzen.dto.DashboardKPIs;
import ci.assurzen.dto.DashboardPeriod;
import ci.assurzen.dto.PartenaireResponse;
import ci.assurzen.dto.ProductionHistoryEntry;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.ProductType;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.Profile;
import ci.assurzen.repositories.AvenantRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refonte Espace Partenaire (2026-10-03) : periodes quarter/year, valeurs de la periode precedente
 * (tendances), repartition par branche et historique mensuel de production.
 */
class DashboardServiceTest {

    private static final long USER_ID = 7L;

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final AvenantRepository avenantRepository = mock(AvenantRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final PartenaireService partenaireService = mock(PartenaireService.class);
    private final CommercialRepository commercialRepository = mock(CommercialRepository.class);
    private final MoisComptableRepository moisComptableRepository = mock(MoisComptableRepository.class);
    private final CommercialPerformanceService performanceService = mock(CommercialPerformanceService.class);

    private final DashboardService service = new DashboardService(contractRepository, profileRepository,
            partenaireRepository, avenantRepository, authService, partenaireService, commercialRepository,
            moisComptableRepository, performanceService);

    private final ProductType auto = ProductType.builder().id(1L).code("AUTO").nom("Automobile").couleur("#2563eb").build();
    private final ProductType sante = ProductType.builder().id(2L).code("SANTE").nom("Sante").build();

    @Test
    void periodeTrimestre_bornesEtPeriodePrecedente() {
        DashboardPeriod.Bornes t3 = DashboardPeriod.quarter.bornes(YearMonth.of(2026, 8));
        assertEquals(YearMonth.of(2026, 7), t3.debut());
        assertEquals(YearMonth.of(2026, 9), t3.fin());
        assertEquals(new DashboardPeriod.Bornes(YearMonth.of(2026, 4), YearMonth.of(2026, 6)), t3.precedente());

        DashboardPeriod.Bornes janvier = DashboardPeriod.month.bornes(YearMonth.of(2026, 1));
        assertEquals(new DashboardPeriod.Bornes(YearMonth.of(2025, 12), YearMonth.of(2025, 12)), janvier.precedente());
        assertNull(DashboardPeriod.all.bornes(YearMonth.of(2026, 1)));
    }

    @Test
    void kpisDuMois_valeursPrecedentesEtRepartitionParBranche() {
        connecterPartenaire();
        YearMonth courant = YearMonth.now(ZoneOffset.UTC);
        when(contractRepository.findByPartenaireIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(
                contrat(1L, auto, 300_000L, ContractStatus.signed, courant),
                contrat(2L, auto, 100_000L, ContractStatus.signed, courant),
                contrat(3L, sante, 100_000L, ContractStatus.signed, courant),
                contrat(4L, sante, 999_000L, ContractStatus.pending, courant),
                contrat(5L, auto, 200_000L, ContractStatus.signed, courant.minusMonths(1)),
                contrat(6L, auto, 50_000L, ContractStatus.pending, courant.minusMonths(1)),
                contrat(7L, auto, 80_000L, ContractStatus.signed, courant.minusMonths(2))));

        DashboardKPIs kpis = service.getKpis(DashboardPeriod.month);

        assertEquals(500_000L, kpis.production());
        assertEquals(2L, kpis.previousTotalContracts());
        assertEquals(1L, kpis.previousSignedContracts());
        assertEquals(200_000L, kpis.previousProduction());
        assertEquals(20_000L, kpis.previousPartnerShare());

        List<DashboardKPIs.BrancheRepartition> branches = kpis.repartitionParBranche();
        assertEquals(2, branches.size());
        assertEquals("AUTO", branches.get(0).code());
        assertEquals(400_000L, branches.get(0).production());
        assertEquals(2L, branches.get(0).nbContrats());
        assertEquals(0.8d, branches.get(0).part());
        assertEquals("#2563eb", branches.get(0).couleur());
        assertEquals(0.2d, branches.get(1).part());
    }

    @Test
    void kpisDuMois_productionSurLaDateDeSignature_contratsCreesSurLaDateDeCreation() {
        connecterPartenaire();
        YearMonth courant = YearMonth.now(ZoneOffset.UTC);
        Contract creeAvantSigneCeMois = contrat(1L, auto, 300_000L, ContractStatus.signed, courant.minusMonths(1));
        creeAvantSigneCeMois.setSignedAt(courant.atDay(2));
        Contract creeCeMoisSigneAvant = contrat(2L, auto, 100_000L, ContractStatus.signed, courant);
        creeCeMoisSigneAvant.setSignedAt(courant.minusMonths(1).atDay(28));
        when(contractRepository.findByPartenaireIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(
                creeAvantSigneCeMois, creeCeMoisSigneAvant,
                contrat(3L, sante, 50_000L, ContractStatus.pending, courant)));

        DashboardKPIs kpis = service.getKpis(DashboardPeriod.month);

        assertEquals(300_000L, kpis.production());
        assertEquals(1L, kpis.signedContracts());
        assertEquals(2L, kpis.totalContracts());
        assertEquals(1L, kpis.pendingContracts());
        assertEquals(0.5d, kpis.conversionRate());
        assertEquals(100_000L, kpis.previousProduction());
        assertEquals(1L, kpis.previousTotalContracts());
        assertEquals(300_000L, kpis.repartitionParBranche().get(0).production());
    }

    @Test
    void kpisToutePeriode_pasDeValeurPrecedente() {
        connecterPartenaire();
        when(contractRepository.findByPartenaireIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(
                contrat(1L, auto, 300_000L, ContractStatus.signed, YearMonth.of(2024, 3))));

        DashboardKPIs kpis = service.getKpis(DashboardPeriod.all);

        assertEquals(300_000L, kpis.production());
        assertNull(kpis.previousProduction());
        assertNull(kpis.previousTotalContracts());
    }

    @Test
    void historiquePartenaire_objectifAnnuelProratiseSurLesMoisDeSonExercice() {
        Partenaire partenaire = connecterPartenaire();
        ExerciceComptable exercice = partenaire.getExerciceComptable();
        ExerciceComptable autreExercice = ExerciceComptable.builder().id(98L).build();
        LocalDate debutCourant = LocalDate.now().withDayOfMonth(1);
        MoisComptable courant = mois(12L, debutCourant, exercice);
        MoisComptable precedent = mois(11L, debutCourant.minusMonths(1), exercice);
        MoisComptable ancien = mois(10L, debutCourant.minusMonths(2), autreExercice);
        when(moisComptableRepository.findByDateDebutLessThanEqualOrderByDateDebutDesc(any()))
                .thenReturn(List.of(courant, precedent, ancien));
        when(moisComptableRepository.countByExerciceComptableId(99L)).thenReturn(12L);
        Contract signeCeMois = contrat(1L, auto, 300_000L, ContractStatus.signed, YearMonth.from(debutCourant));
        signeCeMois.setSignedAt(debutCourant.plusDays(2));
        when(contractRepository.findByStatusAndPartenaireId(ContractStatus.signed, 50L)).thenReturn(List.of(signeCeMois));

        List<ProductionHistoryEntry> historique = service.getProductionHistory(6);

        assertEquals(3, historique.size());
        assertEquals(10L, historique.get(0).moisComptableId());
        assertNull(historique.get(0).objectif());
        assertNull(historique.get(0).objectifSource());

        ProductionHistoryEntry dernier = historique.get(2);
        assertEquals(YearMonth.from(debutCourant).toString(), dernier.mois());
        assertEquals(300_000L, dernier.production());
        assertEquals(1L, dernier.nbContrats());
        assertEquals(1_000_000L, dernier.objectif());
        assertEquals("ANNUEL_PRORATISE", dernier.objectifSource());
        assertTrue(dernier.enCours());
        assertFalse(historique.get(1).enCours());
        assertEquals(0L, historique.get(1).production());
    }

    @Test
    void historiqueCommercial_reprendLaPerformanceMensuelle() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(USER_ID, "commercial", "COMMERCIAL"));
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        Commercial commercial = Commercial.builder().id(3L).build();
        when(commercialRepository.findByProfile_UserId(USER_ID)).thenReturn(Optional.of(commercial));
        LocalDate debut = LocalDate.now().withDayOfMonth(1);
        MoisComptable courant = mois(12L, debut, null);
        when(moisComptableRepository.findByDateDebutLessThanEqualOrderByDateDebutDesc(any())).thenReturn(List.of(courant));
        when(contractRepository.findByStatusAndSalesRepId(ContractStatus.signed, USER_ID)).thenReturn(List.of());
        when(performanceService.resoudrePourLecture(eq(commercial), eq(courant))).thenReturn(CommercialPerformance.builder()
                .objectiveAmount(BigDecimal.valueOf(45_000)).realizedAmount(BigDecimal.valueOf(52_000)).build());

        List<ProductionHistoryEntry> historique = service.getProductionHistory(6);

        assertEquals(1, historique.size());
        assertEquals(52_000L, historique.get(0).production());
        assertEquals(45_000L, historique.get(0).objectif());
        assertEquals("MENSUEL", historique.get(0).objectifSource());
    }

    private Partenaire connecterPartenaire() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(USER_ID, "partenaire", "USER"));
        Profile profile = Profile.builder().id(70L).build();
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        Partenaire partenaire = Partenaire.builder().id(50L).objectifProduction(BigDecimal.valueOf(12_000_000))
                .exerciceComptable(ExerciceComptable.builder().id(99L).build()).build();
        when(partenaireRepository.findByProfile_Id(70L)).thenReturn(List.of(partenaire));
        when(partenaireRepository.findById(50L)).thenReturn(Optional.of(partenaire));
        PartenaireResponse response = mock(PartenaireResponse.class);
        when(partenaireService.findById(50L)).thenReturn(response);
        when(avenantRepository.countByContract_IdIn(anyList())).thenReturn(0L);
        return partenaire;
    }

    private Contract contrat(long id, ProductType type, long prime, ContractStatus statut, YearMonth creation) {
        Produit produit = Produit.builder().id(id).productType(type).build();
        return Contract.builder().id(id).product(produit).premium(prime).partnerShare(prime / 10)
                .commissionAmount(prime / 10).status(statut)
                .createdAt(creation.atDay(5).atStartOfDay().toInstant(ZoneOffset.UTC))
                .build();
    }

    private MoisComptable mois(long id, LocalDate debut, ExerciceComptable exercice) {
        return MoisComptable.builder().id(id).libelle("Mois " + id).exerciceComptable(exercice)
                .dateDebut(debut).dateFin(debut.withDayOfMonth(debut.lengthOfMonth())).build();
    }
}
