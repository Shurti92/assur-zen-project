package ci.assurzen.services;

import ci.assurzen.dto.CommercialPayrollEntry;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.entities.StatutCommercialPerformance;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommercialPerformanceRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre l'exposition des elements de paie du mois (correctif 2026-10-02 : le mois en cours n'est plus
 * filtre, chaque commercial actif apparait, avec un bonus qualifie d'estime tant que la performance
 * n'est pas definitive - §45/§77) et le calcul du total variable.
 */
class CommercialPayrollServiceTest {

    private record Fixture(
            CommercialPayrollService service,
            CommercialPerformanceRepository repository,
            CommercialService commercialService,
            CommercialRepository commercialRepository,
            MoisComptableRepository moisComptableRepository,
            CommercialPerformanceService performanceService
    ) {
    }

    private Fixture newFixture() {
        CommercialPerformanceRepository repository = mock(CommercialPerformanceRepository.class);
        CommercialService commercialService = mock(CommercialService.class);
        CommercialRepository commercialRepository = mock(CommercialRepository.class);
        MoisComptableRepository moisComptableRepository = mock(MoisComptableRepository.class);
        CommercialPerformanceService performanceService = mock(CommercialPerformanceService.class);
        when(moisComptableRepository.findById(10L)).thenReturn(Optional.of(mois()));
        when(performanceService.transactionsParPerformance(any())).thenReturn(Map.of());
        CommercialPayrollService service = new CommercialPayrollService(repository, commercialService, commercialRepository,
                moisComptableRepository, performanceService, mock(CommercialBenefitPaymentService.class));
        return new Fixture(service, repository, commercialService, commercialRepository, moisComptableRepository, performanceService);
    }

    private Commercial commercial(long id, String prenom) {
        User user = new User();
        user.setId(100 + id);
        user.setUsername("commercial" + id);
        Profile profile = Profile.builder().id(200 + id).firstname(prenom).lastname("Koffi").user(user).build();
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).profile(profile)
                .baseSalary(BigDecimal.valueOf(300000)).statut(StatutCommercial.ACTIVE).build();
    }

    private MoisComptable mois() {
        return MoisComptable.builder().id(10L).numero(1).libelle("Janvier 2026")
                .dateDebut(LocalDate.of(2026, 1, 1)).dateFin(LocalDate.of(2026, 1, 31)).build();
    }

    private CommercialPerformance performance(Commercial c, StatutCommercialPerformance statut) {
        return CommercialPerformance.builder()
                .id(c.getId()).commercial(c).moisComptable(mois())
                .objectiveAmount(BigDecimal.valueOf(5_000_000)).realizedAmount(BigDecimal.valueOf(5_000_000))
                .achievementRate(BigDecimal.valueOf(100)).bonusRateSnapshot(BigDecimal.valueOf(5))
                .bonusAmount(BigDecimal.valueOf(250000)).eligible(true)
                .baseSalarySnapshot(BigDecimal.valueOf(300000)).transportBenefitSnapshot(BigDecimal.valueOf(50000))
                .phoneAllowanceSnapshot(BigDecimal.valueOf(20000)).statut(statut).build();
    }

    @Test
    void forMois_moisEnCours_listeChaqueCommercialActifAvecUnBonusEstime() {
        Fixture fx = newFixture();
        Commercial c1 = commercial(1, "Awa");
        Commercial c2 = commercial(2, "Bakary");
        when(fx.repository().findByMoisComptableId(10L)).thenReturn(List.of()); // aucune ligne persistee
        when(fx.commercialRepository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(List.of(c2, c1));
        when(fx.performanceService().resoudrePourLecture(any(), any())).thenAnswer(inv ->
                performance(inv.getArgument(0), StatutCommercialPerformance.ELIGIBLE));

        List<CommercialPayrollEntry> entries = fx.service().forMois(10L);

        assertEquals(2, entries.size());
        assertEquals(1L, entries.get(0).commercialId()); // tri par nom
        assertTrue(entries.get(0).estime());
        assertEquals(StatutCommercialPerformance.ELIGIBLE, entries.get(0).statut());
    }

    @Test
    void forMois_performanceDefinitive_nEstPasEstimeeEtBonusPayable() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, "Jean");
        CommercialPerformance validee = performance(c, StatutCommercialPerformance.VALIDATED);
        when(fx.repository().findByMoisComptableId(10L)).thenReturn(List.of(validee));
        when(fx.commercialRepository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(List.of(c));
        when(fx.performanceService().resoudrePourLecture(any(), any())).thenReturn(validee);
        when(fx.performanceService().estPayable(validee, null)).thenReturn(true);

        List<CommercialPayrollEntry> entries = fx.service().forMois(10L);

        assertEquals(1, entries.size()); // commercial present a la fois en base et parmi les actifs : une seule ligne
        assertFalse(entries.get(0).estime());
        assertTrue(entries.get(0).bonusPayable());
    }

    @Test
    void forMois_calculeLeTotalVariableCommeLaSommeDes4Composantes() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, "Jean");
        when(fx.commercialRepository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(List.of(c));
        when(fx.performanceService().resoudrePourLecture(any(), any())).thenReturn(performance(c, StatutCommercialPerformance.PAID));

        CommercialPayrollEntry entry = fx.service().forMois(10L).get(0);

        assertEquals("COM-000001", entry.employeeCode());
        assertEquals("Jean Koffi", entry.commercialNom());
        // 300 000 (base) + 50 000 (transport) + 20 000 (telephone) + 250 000 (bonus) = 620 000.
        assertEquals(0, BigDecimal.valueOf(620000).compareTo(entry.totalVariable()));
    }

    @Test
    void masquerTelephone_neDevoileJamaisLeNumeroComplet() {
        assertEquals("07 •• •• 12", CommercialPayrollService.masquerTelephone("0700000012"));
        assertEquals("22 •• •• 12", CommercialPayrollService.masquerTelephone("+225 07 00 00 00 12"));
        assertEquals(null, CommercialPayrollService.masquerTelephone(null));
    }

    @Test
    void forMois_moisInconnu_estRejeteAvec404() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().forMois(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void forMois_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().forMois(10L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.repository(), never()).findByMoisComptableId(any());
    }
}
