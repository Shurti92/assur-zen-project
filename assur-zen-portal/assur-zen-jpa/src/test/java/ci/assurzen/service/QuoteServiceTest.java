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
        TariffInput in = new TariffInput("moto", "tiers-simple-moto", "MOTO", 12, 6375L, 925L, 500L, 128L, 8000L, true);
        assertThrows(MobileApiException.class, () -> service.enregistrerGrille(new TariffUpdate(java.util.List.of(in))));
    }
}
