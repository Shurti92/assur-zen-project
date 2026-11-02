package ci.assurzen.services;

import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.ContractRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre la definition reelle de la "production" d'un commercial (§22) : PAS mockee ici (contrairement
 * a CommercialPerformanceServiceTest, qui mocke ce collaborateur) - on verifie le filtrage effectif par
 * bornes de mois (inclusives), l'exclusion des contrats sans date de signature, et le garde-fou
 * division-par-zero de computeTauxRealisation.
 */
class CommercialProductionServiceTest {

    private ContractRepository contractRepository;
    private CommercialProductionService service;

    private void newFixture() {
        contractRepository = mock(ContractRepository.class);
        service = new CommercialProductionService(contractRepository);
    }

    private Commercial commercialAvecUser(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("commercial" + userId);
        Profile profile = Profile.builder().id(1L).firstname("Jean").lastname("Koffi").user(user).build();
        return Commercial.builder().id(1L).employeeCode("COM-000001").profile(profile).build();
    }

    private MoisComptable janvier2026() {
        return MoisComptable.builder().id(10L).numero(1).libelle("Janvier 2026")
                .dateDebut(LocalDate.of(2026, 1, 1)).dateFin(LocalDate.of(2026, 1, 31)).cloture(false).build();
    }

    private Contract contrat(Long premium, LocalDate signedAt) {
        return Contract.builder().id(100L + (premium == null ? 0 : premium)).premium(premium)
                .status(ContractStatus.signed).signedAt(signedAt).build();
    }

    // ---------------------------------------------------------------- computeRealise() / contratsEligibles()

    @Test
    void computeRealise_sommeLesPrimesDesContratsSignesDansLesBornesDuMois() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of(
                contrat(1_500_000L, LocalDate.of(2026, 1, 10)),
                contrat(2_000_000L, LocalDate.of(2026, 1, 20)),
                contrat(2_000_000L, LocalDate.of(2026, 1, 25))
        ));

        BigDecimal realise = service.computeRealise(c, mois);

        assertEquals(0, BigDecimal.valueOf(5_500_000).compareTo(realise));
    }

    @Test
    void computeRealise_bornesInclusives_dateDebutEtDateFinSontComptees() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of(
                contrat(1_000_000L, mois.getDateDebut()),
                contrat(2_000_000L, mois.getDateFin())
        ));

        BigDecimal realise = service.computeRealise(c, mois);

        assertEquals(0, BigDecimal.valueOf(3_000_000).compareTo(realise));
    }

    @Test
    void computeRealise_excludesContratSigneAvantOuApresLeMois() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of(
                contrat(1_000_000L, LocalDate.of(2025, 12, 31)),  // veille du mois
                contrat(2_000_000L, LocalDate.of(2026, 2, 1)),    // lendemain du mois
                contrat(3_000_000L, LocalDate.of(2026, 1, 15))    // dans le mois
        ));

        BigDecimal realise = service.computeRealise(c, mois);
        long nombre = service.countContratsEligibles(c, mois);

        assertEquals(0, BigDecimal.valueOf(3_000_000).compareTo(realise));
        assertEquals(1, nombre);
    }

    @Test
    void computeRealise_excludesContratSansDateDeSignature() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of(
                contrat(1_000_000L, null),
                contrat(2_000_000L, LocalDate.of(2026, 1, 15))
        ));

        BigDecimal realise = service.computeRealise(c, mois);

        assertEquals(0, BigDecimal.valueOf(2_000_000).compareTo(realise));
    }

    @Test
    void computeRealise_ignoreUnContratSansPrimeRenseignee() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of(
                contrat(null, LocalDate.of(2026, 1, 5)),
                contrat(2_000_000L, LocalDate.of(2026, 1, 15))
        ));

        BigDecimal realise = service.computeRealise(c, mois);

        assertEquals(0, BigDecimal.valueOf(2_000_000).compareTo(realise));
    }

    @Test
    void computeRealise_aucunContratEligible_renvoieZero() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(eq(ContractStatus.signed), eq(42L))).thenReturn(List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(service.computeRealise(c, mois)));
        assertEquals(0, service.countContratsEligibles(c, mois));
    }

    @Test
    void computeRealise_neInterrogeQueLesContratsDuUserDuCommercial() {
        newFixture();
        Commercial c = commercialAvecUser(42L);
        MoisComptable mois = janvier2026();
        when(contractRepository.findByStatusAndSalesRepId(ContractStatus.signed, 42L)).thenReturn(List.of());

        service.computeRealise(c, mois);

        org.mockito.Mockito.verify(contractRepository).findByStatusAndSalesRepId(ContractStatus.signed, 42L);
    }

    // ---------------------------------------------------------------- computeTauxRealisation()

    @Test
    void computeTauxRealisation_calculStandard() {
        newFixture();
        BigDecimal taux = service.computeTauxRealisation(BigDecimal.valueOf(4_500_000), BigDecimal.valueOf(5_000_000));
        assertEquals(0, new BigDecimal("90.0000").compareTo(taux));
    }

    @Test
    void computeTauxRealisation_objectifZero_renvoieZeroSansDiviserParZero() {
        newFixture();
        BigDecimal taux = service.computeTauxRealisation(BigDecimal.valueOf(2_000_000), BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(taux));
    }

    @Test
    void computeTauxRealisation_objectifNull_renvoieZero() {
        newFixture();
        BigDecimal taux = service.computeTauxRealisation(BigDecimal.valueOf(2_000_000), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(taux));
    }
}
