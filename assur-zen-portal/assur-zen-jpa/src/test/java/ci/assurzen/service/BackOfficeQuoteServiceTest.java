package ci.assurzen.service;

import ci.assurzen.entities.Quote;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CommercialService;
import ci.assurzen.services.DynamicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackOfficeQuoteServiceTest {

    private QuoteRepository repo;
    private AuthService auth;
    private CommercialService commercial;
    private BackOfficeQuoteService service;

    @BeforeEach
    void init() {
        repo = mock(QuoteRepository.class);
        auth = mock(AuthService.class);
        commercial = mock(CommercialService.class);
        MobileQuoteService mobile = new MobileQuoteService(mock(QuoteService.class), repo, mock(DynamicConfigurationService.class));
        service = new BackOfficeQuoteService(mobile, repo, auth, commercial);
        when(repo.save(any(Quote.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void utilisateur(long id, String roles) {
        AuthenticatedUserPrincipal p = new AuthenticatedUserPrincipal(id, "u" + id, roles);
        when(auth.currentPrincipal()).thenReturn(p);
        when(commercial.isAdmin(p)).thenReturn(roles.contains("ADMIN"));
    }

    private Quote devis(String ref, Long createur) {
        Quote q = new Quote();
        q.setReference(ref);
        q.setProductKey("moto");
        q.setFormulaId("tiers-simple-moto");
        q.setFormulaName("Tiers Simple Moto");
        q.setVehicleType("MOTO");
        q.setDurationMonths(12);
        q.setEffectiveDate(LocalDate.now().plusDays(10));
        q.setExpiryDate(LocalDate.now().plusDays(375));
        q.setNetPremium(6447L);
        q.setTaxes(925L);
        q.setAccessories(500L);
        q.setFga(128L);
        q.setTotal(8000L);
        q.setValidUntil(LocalDate.now().plusDays(5));
        q.setChannel("BACKOFFICE");
        q.setCreatedByUserId(createur);
        q.setProspectName("Kouame Yao");
        q.setProspectPhone("0701020304");
        return q;
    }

    @Test
    void commercial_neVoitQueSesDevis() {
        utilisateur(5L, "COMMERCIAL");
        when(repo.findByCreatedByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(devis("DEV-2026-000001", 5L)));
        assertEquals(1, service.lister(null, null).items().size());
        verify(repo).findByCreatedByUserIdOrderByCreatedAtDesc(5L);
    }

    @Test
    void commercial_neLitPasLeDevisDUnCollegue() {
        utilisateur(5L, "COMMERCIAL");
        when(repo.findByReference("DEV-2026-000002")).thenReturn(Optional.of(devis("DEV-2026-000002", 9L)));
        assertEquals(404, assertThrows(MobileApiException.class, () -> service.detail("DEV-2026-000002")).getHttpStatus());
    }

    @Test
    void administrateur_voitTout_etFiltre() {
        utilisateur(1L, "ADMIN");
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(devis("DEV-2026-000001", 5L), devis("DEV-2026-000002", 9L)));
        assertEquals(2, service.lister(null, null).items().size());
        assertEquals(2, service.lister("calcule", "kouame").items().size());
        assertEquals(0, service.lister("ACCEPTE", null).items().size());
        assertEquals(0, service.lister(null, "inconnu").items().size());
    }

    @Test
    void acceptation_enregistreLAccordDuProspect() {
        utilisateur(1L, "ADMIN");
        when(repo.findByReference("DEV-2026-000001")).thenReturn(Optional.of(devis("DEV-2026-000001", 5L)));
        assertEquals("ACCEPTE", service.accepter("DEV-2026-000001").status());
    }

    @Test
    void acceptation_unDevisPerimeEstRefuse() {
        utilisateur(1L, "ADMIN");
        Quote q = devis("DEV-2026-000003", 5L);
        q.setValidUntil(LocalDate.now().minusDays(1));
        when(repo.findByReference("DEV-2026-000003")).thenReturn(Optional.of(q));
        assertEquals("QUOTE_EXPIRED", assertThrows(MobileApiException.class, () -> service.accepter("DEV-2026-000003")).getCode());
    }
}
