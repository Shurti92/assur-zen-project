package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.Quote;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.services.DynamicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileQuoteServiceTest {

    private QuoteService calcul;
    private QuoteRepository repo;
    private DynamicConfigurationService config;
    private MobileQuoteService service;
    private final LocalDate today = LocalDate.now(ZoneId.of("Africa/Abidjan"));

    @BeforeEach
    void init() {
        calcul = mock(QuoteService.class);
        repo = mock(QuoteRepository.class);
        config = mock(DynamicConfigurationService.class);
        service = new MobileQuoteService(calcul, repo, config);
        when(config.getLong(eq("QUOTE_VALIDITY_DAYS"), any())).thenReturn(30L);
        when(repo.save(any(Quote.class))).thenAnswer(i -> {
            Quote q = i.getArgument(0);
            if (q.getId() == null) q.setId(42L);
            return q;
        });
    }

    private MotoSimulation simulation(LocalDate effet) {
        return new MotoSimulation("moto", "tiers-simple-moto", "Tiers Simple Moto", "MOTO", 12, effet, effet.plusMonths(12).minusDays(1),
                new PremiumBreakdown(6447, 925, 500, 128, 8000, "XOF"), QuoteService.garantiesTiersSimpleMoto());
    }

    private MotoSimulationRequest demande(LocalDate effet) {
        return new MotoSimulationRequest("MOTO", 125, "Yamaha", "Crypton", 12, "1234AB01", null, effet, null);
    }

    @Test
    void creation_figeLePrixEtGenereLaReference() {
        when(calcul.simulerMoto(any())).thenReturn(simulation(today.plusDays(60)));
        QuoteView v = service.creerMoto(7L, demande(today.plusDays(60)));
        assertEquals(String.format("DEV-%d-000042", today.getYear()), v.reference());
        assertEquals("CALCULE", v.status());
        assertTrue(v.canAccept());
        assertEquals(8000, v.premium().total());
        assertEquals(today.plusDays(30), v.validUntil());
    }

    @Test
    void creation_laValiditeNeDepassePasLaDateDEffet() {
        when(calcul.simulerMoto(any())).thenReturn(simulation(today.plusDays(5)));
        assertEquals(today.plusDays(5), service.creerMoto(7L, demande(today.plusDays(5))).validUntil());
    }

    private Quote devis(String statut, LocalDate validite) {
        Quote q = new Quote();
        q.setId(1L);
        q.setReference("DEV-2026-000001");
        q.setUserId(7L);
        q.setProductKey("moto");
        q.setFormulaId("tiers-simple-moto");
        q.setFormulaName("Tiers Simple Moto");
        q.setVehicleType("MOTO");
        q.setDurationMonths(12);
        q.setEffectiveDate(today.plusDays(10));
        q.setExpiryDate(today.plusDays(375));
        q.setNetPremium(6447L);
        q.setTaxes(925L);
        q.setAccessories(500L);
        q.setFga(128L);
        q.setTotal(8000L);
        q.setStatus(statut);
        q.setValidUntil(validite);
        return q;
    }

    @Test
    void acceptation_unDevisValideDevientAccepte() {
        when(repo.findByReferenceAndUserId("DEV-2026-000001", 7L)).thenReturn(Optional.of(devis("CALCULE", today.plusDays(3))));
        QuoteView v = service.accepter(7L, "DEV-2026-000001");
        assertEquals("ACCEPTE", v.status());
        assertNotNull(v.acceptedAt());
        assertFalse(v.canAccept());
    }

    @Test
    void acceptation_unDevisPerimeEstRefuse() {
        when(repo.findByReferenceAndUserId("DEV-2026-000001", 7L)).thenReturn(Optional.of(devis("CALCULE", today.minusDays(1))));
        MobileApiException e = assertThrows(MobileApiException.class, () -> service.accepter(7L, "DEV-2026-000001"));
        assertEquals(409, e.getHttpStatus());
        assertEquals("QUOTE_EXPIRED", e.getCode());
    }

    @Test
    void acceptation_unDevisDUnAutreClientEstIntrouvable() {
        when(repo.findByReferenceAndUserId("DEV-2026-000001", 8L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(MobileApiException.class, () -> service.detail(8L, "DEV-2026-000001")).getHttpStatus());
    }
}
