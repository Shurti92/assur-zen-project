package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.Quote;
import ci.assurzen.entities.QuoteEmail;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.QuoteEmailRepository;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteEmailServiceTest {

    private BackOfficeQuoteService backOffice;
    private QuoteRepository quotes;
    private QuoteEmailRepository emails;
    private MailService mail;
    private AuthService auth;
    private ProfileRepository profiles;
    private QuoteEmailService service;
    private Quote devis;

    @BeforeEach
    void init() {
        backOffice = mock(BackOfficeQuoteService.class);
        quotes = mock(QuoteRepository.class);
        emails = mock(QuoteEmailRepository.class);
        mail = mock(MailService.class);
        auth = mock(AuthService.class);
        profiles = mock(ProfileRepository.class);
        DynamicConfigurationService config = mock(DynamicConfigurationService.class);
        when(config.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        MobileQuoteService mobile = new MobileQuoteService(mock(QuoteService.class), quotes, config);
        service = new QuoteEmailService(backOffice, mobile, quotes, emails, new QuoteDocumentService(config), mail, auth, profiles);
        when(auth.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(7L, "0700000001", "COMMERCIAL"));
        Profile profil = new Profile();
        profil.setFirstname("Awa");
        profil.setLastname("Koné");
        profil.setEmail("awa@assurzen.ci");
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profil));
        when(quotes.save(any(Quote.class))).thenAnswer(i -> i.getArgument(0));
        devis = devis();
        when(backOffice.trouver("DEV-2026-000001")).thenReturn(devis);
    }

    private Quote devis() {
        Quote q = new Quote();
        q.setId(1L);
        q.setReference("DEV-2026-000001");
        q.setProductKey("automobile-vtc");
        q.setFormulaId("tiers-simple-vtc");
        q.setFormulaName("Tiers Simple VTC");
        q.setVehicleType("VTC");
        q.setBrand("Toyota");
        q.setModel("Corolla <b>");
        q.setFiscalPower(6);
        q.setSeats(5);
        q.setRegistrationNumber("1234AB01");
        q.setDurationMonths(12);
        q.setEffectiveDate(LocalDate.now().plusDays(5));
        q.setExpiryDate(LocalDate.now().plusDays(370));
        q.setNetPremium(181363L);
        q.setTaxes(0L);
        q.setAccessories(2024L);
        q.setFga(280L);
        q.setCedeao(1000L);
        q.setAttestation(1000L);
        q.setDiscountAmount(18136L);
        q.setDiscountPercent(new BigDecimal("10"));
        q.setTotal(167531L);
        q.setValidUntil(LocalDate.now().plusDays(5));
        q.setProspectName("Kouamé <script>");
        q.setProspectPhone("0509990004");
        q.setProspectEmail("prospect@example.com");
        q.setCreatedByUserId(7L);
        return q;
    }

    @Test
    void envoi_reussi_metAJourLesCompteursEtTrace() {
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn(null);
        QuoteView v = service.envoyer("DEV-2026-000001", new SendEmailRequest(null, "Bonjour, voici l'offre.", true, true));
        assertEquals(1, v.emailSentCount());
        assertEquals("prospect@example.com", v.lastEmailTo());
        assertEquals("Awa Koné", v.lastEmailSentBy());
        ArgumentCaptor<byte[]> pdf = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(mail).envoyerAvecPiece(eq("customer@assurzen.ci"), eq("prospect@example.com"), eq("awa@assurzen.ci"), eq("Votre devis AssurZen DEV-2026-000001 · Assurance automobile VTC"),
                corps.capture(), eq("Devis-DEV-2026-000001.pdf"), pdf.capture());
        assertEquals("%PDF", new String(pdf.getValue(), 0, 4));
        String html = corps.getValue();
        assertFalse(html.contains("<script>"), "le nom du prospect doit etre echappe");
        assertFalse(html.contains("Corolla <b>"), "le modele doit etre echappe");
        assertTrue(html.contains("Corolla &lt;b&gt;"));
        assertTrue(html.contains("167 531 F"));
        assertTrue(html.contains("Remise 10 %"));
        String texte = org.springframework.web.util.HtmlUtils.htmlUnescape(html);
        assertTrue(texte.contains("Une offre AssurZen Côte d'Ivoire"));
        assertTrue(texte.contains("Besoin d'aide ? Répondez à cet e-mail ou appelez le service client."));
        assertFalse(texte.contains("Sanlam"));
        assertTrue(html.contains("#1b3b8f") && html.contains("#14265c") && html.contains("#ec7c1e"), "palette AssurZen");
        assertFalse(html.contains("#4f46e5") || html.contains("#7c3aed"), "plus de violet");
        assertTrue(texte.contains(">AssurZen Assurance<"));
        assertTrue(texte.contains("Votre sérénité, notre priorité."));
        assertFalse(texte.contains("Votre assurance, simplement."));
        assertTrue(texte.contains("Le devis détaillé est joint à cet e-mail (PDF)."));
        verify(emails).save(any(QuoteEmail.class));
    }

    @Test
    void envoi_sansPdf_neJointRien() {
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn(null);
        service.envoyer("DEV-2026-000001", new SendEmailRequest(null, null, false, false));
        verify(mail).envoyerAvecPiece(eq("customer@assurzen.ci"), anyString(), eq(null), anyString(), anyString(), anyString(), eq(null));
    }

    @Test
    void envoi_nouvelleAdresse_remplaceLAdresseDuProspect() {
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn(null);
        QuoteView v = service.envoyer("DEV-2026-000001", new SendEmailRequest("autre@example.com", null, true, false));
        assertEquals("autre@example.com", v.prospectEmail());
    }

    @Test
    void envoi_emailInvalideOuAbsent_estRefuse() {
        assertEquals("email", assertThrows(MobileApiException.class,
                () -> service.envoyer("DEV-2026-000001", new SendEmailRequest("pas-un-email", null, true, false))).getField());
        devis.setProspectEmail(null);
        assertEquals("email", assertThrows(MobileApiException.class, () -> service.envoyer("DEV-2026-000001", null)).getField());
        verify(mail, never()).envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void envoi_devisPerime_409() {
        devis.setValidUntil(LocalDate.now().minusDays(1));
        assertEquals("QUOTE_EXPIRED", assertThrows(MobileApiException.class, () -> service.envoyer("DEV-2026-000001", null)).getCode());
    }

    @Test
    void envoi_limiteDe5ParJour_429() {
        when(emails.countByQuoteIdAndSentAtAfter(anyLong(), any(Instant.class))).thenReturn(5L);
        assertEquals(429, assertThrows(MobileApiException.class, () -> service.envoyer("DEV-2026-000001", null)).getHttpStatus());
    }

    @Test
    void envoi_echecOuMessagerieDesactivee_nEnregistreRien() {
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn("DISABLED");
        assertEquals("MAIL_DISABLED", assertThrows(MobileApiException.class, () -> service.envoyer("DEV-2026-000001", null)).getCode());
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn("Connection refused");
        assertEquals("MAIL_SEND_FAILED", assertThrows(MobileApiException.class, () -> service.envoyer("DEV-2026-000001", null)).getCode());
        verify(emails, never()).save(any(QuoteEmail.class));
        assertEquals(0, devis.getEmailSentCount());
    }

    @Test
    void creation_avecEnvoiEchoue_neFaitPasEchouerLaCreation() {
        when(mail.envoyerAvecPiece(anyString(), anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn("Connection refused");
        QuoteView cree = new MobileQuoteService(mock(QuoteService.class), quotes, mock(DynamicConfigurationService.class)).vue(devis);
        assertSame(cree.reference(), service.envoyerSiDemande(cree, true).reference());
        assertSame(cree, service.envoyerSiDemande(cree, false));
    }
}
