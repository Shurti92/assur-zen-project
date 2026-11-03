package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.QuoteTariff;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteTariffRepository;
import ci.assurzen.services.CommercialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    private QuoteTariffRepository repo;
    private QuoteService service;
    private final AuthService auth = mock(AuthService.class);
    private final ci.assurzen.repositories.DynamicConfigurationRepository brandsRepo = mock(ci.assurzen.repositories.DynamicConfigurationRepository.class);
    private final ci.assurzen.services.DynamicConfigurationService config = mock(ci.assurzen.services.DynamicConfigurationService.class);

    @BeforeEach
    void init() {
        repo = mock(QuoteTariffRepository.class);
        service = new QuoteService(repo, mock(CommercialService.class), auth, config, brandsRepo, mock(ci.assurzen.services.ConfigurationCacheService.class));
        QuoteTariff t = new QuoteTariff();
        t.setDurationMonths(12);
        t.setNetPremium(6447L);
        t.setTaxes(925L);
        t.setAccessories(500L);
        t.setFga(128L);
        t.setTotal(8000L);
        when(repo.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty());
        when(repo.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths("moto", "tiers-simple-moto", "MOTO", 12))
                .thenReturn(Optional.of(t));
        when(repo.findByProductKeyAndActiveTrue("automobile-vtc")).thenReturn(java.util.List.of(
                vtc(1, 23800L), vtc(3, 54160L), vtc(6, 98732L), vtc(12, 185667L)));
        when(config.getString("QUOTE_VTC_MAX_DISCOUNT_PERCENT", "10")).thenReturn("10");
    }

    private MotoSimulationRequest demande(String immat, String chassis, LocalDate date, int mois) {
        return new MotoSimulationRequest("MOTO", 125, "Yamaha", "Crypton", mois, immat, chassis, date, null);
    }

    @Test
    void simulation_retourneLaPrimeDecomposeeEtLesGaranties() {
        MotoSimulation s = service.simulerMoto(demande("1234AB01", null, LocalDate.now(), 12));
        assertEquals(8000L, s.premium().total());
        assertEquals(s.premium().total(), s.premium().netPremium() + s.premium().taxes() + s.premium().accessories() + s.premium().fga());
        assertEquals(LocalDate.now().plusMonths(12).minusDays(1), s.expiryDate());
        assertEquals(4, s.guarantees().size());
        assertEquals(3, s.guarantees().get(3).details().size());
    }

    private static QuoteTariff vtc(int mois, long total) {
        QuoteTariff t = new QuoteTariff();
        t.setProductKey("automobile-vtc");
        t.setFormulaId("tiers-simple-vtc");
        t.setVehicleCategory("VTC");
        t.setDurationMonths(mois);
        t.setAccessories(2024L);
        t.setCedeao(1000L);
        t.setAttestation(1000L);
        t.setFga(280L);
        t.setTaxes(0L);
        t.setNetPremium(total - 4304L);
        t.setTotal(total);
        t.setMinFiscalPower(3);
        t.setMinSeats(4);
        t.setMaxSeats(8);
        return t;
    }

    private VtcSimulationRequest vtcDemande(int cv, int places, int mois, java.math.BigDecimal remise) {
        return new VtcSimulationRequest(cv, "ESSENCE", "Toyota", "Corolla", 2019, places, true, "Yango", mois, "1234AB01", null,
                LocalDate.now(), null, remise);
    }

    @Test
    void vtc_les4DureesDonnentLesTotauxAnnonces() {
        assertEquals(23800L, service.simulerVtc(vtcDemande(5, 5, 1, null), false).premium().total());
        assertEquals(54160L, service.simulerVtc(vtcDemande(5, 5, 3, null), false).premium().total());
        assertEquals(98732L, service.simulerVtc(vtcDemande(5, 5, 6, null), false).premium().total());
        VtcSimulation s = service.simulerVtc(vtcDemande(5, 5, 12, null), false);
        assertEquals(185667L, s.premium().total());
        assertEquals(181363L, s.premium().netPremium());
        assertEquals(1000L, s.premium().cedeao());
        assertEquals(1000L, s.premium().attestation());
        assertEquals(280L, s.premium().fga());
        assertEquals(2024L, s.premium().accessories());
        assertEquals(2, s.guarantees().size());
    }

    @Test
    void vtc_refuseUnVehiculeHorsCriteres() {
        assertEquals("seats", assertThrows(MobileApiException.class, () -> service.simulerVtc(vtcDemande(5, 3, 12, null), false)).getField());
        assertEquals("seats", assertThrows(MobileApiException.class, () -> service.simulerVtc(vtcDemande(5, 9, 12, null), false)).getField());
        assertEquals("seats", assertThrows(MobileApiException.class, () -> service.simulerVtc(vtcDemande(2, 5, 12, null), false)).getField());
        assertEquals("durationMonths", assertThrows(MobileApiException.class, () -> service.simulerVtc(vtcDemande(5, 5, 2, null), false)).getField());
    }

    @Test
    void vtc_remiseSurLaPrimeNetteEtPlafonnee() {
        VtcSimulation s = service.simulerVtc(vtcDemande(5, 5, 12, new java.math.BigDecimal("10")), true);
        assertEquals(18136L, s.premium().discount());
        assertEquals(185667L - 18136L, s.premium().total());
        assertEquals("discountPercent", assertThrows(MobileApiException.class,
                () -> service.simulerVtc(vtcDemande(5, 5, 12, new java.math.BigDecimal("10.5")), true)).getField());
    }

    @Test
    void vtc_pasDeRemiseHorsBackOffice() {
        assertEquals("discountPercent", assertThrows(MobileApiException.class,
                () -> service.simulerVtc(vtcDemande(5, 5, 12, new java.math.BigDecimal("5")), false)).getField());
        assertDoesNotThrow(() -> service.simulerVtc(vtcDemande(5, 5, 12, java.math.BigDecimal.ZERO), false));
    }

    @Test
    void vtc_anneeDansLeFuturRefusee() {
        VtcSimulationRequest r = new VtcSimulationRequest(5, "ESSENCE", "Toyota", "Corolla", LocalDate.now().getYear() + 2, 5, true, null, 12,
                "1234AB01", null, LocalDate.now(), null, null);
        assertEquals("firstRegistrationYear", assertThrows(MobileApiException.class, () -> service.simulerVtc(r, false)).getField());
    }

    @Test
    void grille_leTotalInclutCedeaoEtAttestation() {
        TariffInput ok = new TariffInput("automobile-vtc", "tiers-simple-vtc", "VTC", 12, 181363L, 0L, 2024L, 280L, 1000L, 1000L, 185667L, true, 3, 4, 8);
        when(repo.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(anyString(), anyString(), anyString(), anyInt())).thenReturn(Optional.empty());
        when(auth.currentPrincipal()).thenReturn(new ci.assurzen.security.AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        assertDoesNotThrow(() -> service.enregistrerGrille(new TariffUpdate(java.util.List.of(ok))));
        TariffInput ko = new TariffInput("automobile-vtc", "tiers-simple-vtc", "VTC", 12, 181363L, 0L, 2024L, 280L, null, null, 185667L, true, 3, 4, 8);
        assertThrows(MobileApiException.class, () -> service.enregistrerGrille(new TariffUpdate(java.util.List.of(ko))));
    }

    @Test
    void marques_enregistrementDedoublonneEtStockeDansLaConfiguration() {
        when(auth.currentPrincipal()).thenReturn(new ci.assurzen.security.AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        ci.assurzen.entities.DynamicConfiguration c = new ci.assurzen.entities.DynamicConfiguration();
        when(brandsRepo.findByConfigKey("QUOTE_MOTO_BRANDS")).thenReturn(Optional.of(c));
        BrandList r = service.enregistrerMarques(new BrandUpdate(java.util.List.of("Yamaha", " Honda ", "yamaha", "Jialing")));
        assertEquals(java.util.List.of("Yamaha", "Honda", "Jialing"), r.brands());
        assertEquals("Yamaha, Honda, Jialing", c.getConfigValue());
    }

    @Test
    void marques_listeSansDoublonNiVide() {
        when(config.getString("QUOTE_MOTO_BRANDS", "")).thenReturn(" Yamaha, honda ;Yamaha\nSuzuki,, ");
        assertEquals(java.util.List.of("Yamaha", "honda", "Suzuki"), service.marques());
        when(config.getString("QUOTE_MOTO_BRANDS", "")).thenReturn("");
        assertTrue(service.marques().isEmpty());
    }

    @Test
    void simulation_refuseUneDateDEffetPassee() {
        MobileApiException e = assertThrows(MobileApiException.class,
                () -> service.simulerMoto(demande("1234AB01", null, LocalDate.now().minusDays(1), 12)));
        assertEquals("effectiveDate", e.getField());
    }

    @Test
    void simulation_exigeImmatriculationOuChassis() {
        assertThrows(MobileApiException.class, () -> service.simulerMoto(demande(" ", null, LocalDate.now(), 12)));
        assertDoesNotThrow(() -> service.simulerMoto(demande(null, "LC1234567", LocalDate.now(), 12)));
    }

    @Test
    void simulation_refuseUneDureeNonProposee() {
        assertThrows(MobileApiException.class, () -> service.simulerMoto(demande("1234AB01", null, LocalDate.now(), 6)));
    }

    @Test
    void grille_refuseUnTotalIncoherent() {
        TariffInput in = new TariffInput("moto", "tiers-simple-moto", "MOTO", 12, 6375L, 925L, 500L, 128L, null, null, 8000L, true, null, null, null);
        assertThrows(MobileApiException.class, () -> service.enregistrerGrille(new TariffUpdate(java.util.List.of(in))));
    }
}
