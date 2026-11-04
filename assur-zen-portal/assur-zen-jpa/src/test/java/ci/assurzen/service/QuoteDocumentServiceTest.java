package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.services.DynamicConfigurationService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verrouille le gabarit valide de l'e-mail et du PDF d'un devis (maquettes validees par l'utilisateur) : en-tete, slogan,
 * « Total a payer », pied de page avec accents, palette AssurZen. Toute modification volontaire du gabarit passe par une
 * nouvelle maquette validee ET par la mise a jour de ce test.
 */
class QuoteDocumentServiceTest {

    private QuoteDocumentService docs;

    @BeforeEach
    void init() {
        // Aucune valeur en configuration : ce sont les valeurs PAR DEFAUT du code qui sont verifiees (base reinitialisee).
        DynamicConfigurationService config = mock(DynamicConfigurationService.class);
        when(config.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        docs = new QuoteDocumentService(config);
    }

    private QuoteView vtc() {
        return new QuoteView("DEV-2026-000021", "CALCULE", true, "automobile-vtc", "tiers-simple-vtc", "Tiers Simple VTC", "VTC", null, "Toyota", "Corolla",
                "1234AB01", null, 12, LocalDate.of(2026, 11, 4), LocalDate.of(2027, 11, 3), LocalDate.of(2026, 12, 3),
                new PremiumBreakdown(181363, 0, 2024, 280, 1000, 1000, 9068, 176599, "XOF"), QuoteService.garantiesTiersSimpleVtc(), Instant.now(), null,
                "BACKOFFICE", "Kouassi Yao", "0701020304", 1L, 5, "ESSENCE", 5, 2019, true, "Yango", new BigDecimal("5"),
                "yao@example.com", 0, null, null, null);
    }

    private QuoteView moto() {
        return new QuoteView("DEV-2026-000009", "ACCEPTE", false, "moto", "tiers-simple-moto", "Tiers Simple Moto", "MOTO", 125, "Yamaha", "Crypton",
                "5678CD01", null, 12, LocalDate.of(2026, 11, 4), LocalDate.of(2027, 11, 3), LocalDate.of(2026, 12, 3),
                new PremiumBreakdown(6447, 925, 500, 128, 8000, "XOF"), QuoteService.garantiesTiersSimpleMoto(), Instant.now(), Instant.now(),
                "BACKOFFICE", "Traoré Awa", "0505050505", 1L, null, null, null, null, null, null, null,
                null, 0, null, null, null);
    }

    @Test
    void email_gabaritValide() {
        String html = docs.html(vtc(), "Je reste disponible.", "Aïcha K.", true);
        String texte = HtmlUtils.htmlUnescape(html);
        assertTrue(texte.contains(">AssurZen Assurance<"), "nom dans l'en-tete");
        assertTrue(texte.contains("Votre sérénité, notre priorité."), "slogan officiel");
        assertTrue(texte.contains("Merci de votre confiance. Voici le devis que nous avons préparé pour votre véhicule."));
        assertTrue(texte.contains("Total à payer"));
        assertTrue(texte.contains("Accessoires, CEDEAO, attestation, FGA"), "ligne de frais regroupee");
        assertTrue(texte.contains("— Aïcha K., conseiller AssurZen"));
        assertTrue(texte.contains("✓ Responsabilité civile") && texte.contains("✓ Défense et Recours"));
        assertTrue(texte.contains("Ce devis est une proposition et ne constitue pas un contrat. Il devient un contrat après acceptation et paiement."));
        // pied de page : accents corrects, trois lignes
        assertTrue(texte.contains("Une offre AssurZen Côte d'Ivoire"));
        assertTrue(texte.contains("Besoin d'aide ? Répondez à cet e-mail ou appelez le service client."));
        assertTrue(texte.contains("Le devis détaillé est joint à cet e-mail (PDF)."));
        assertFalse(texte.contains("?te") || texte.contains("R?pondez"), "accents non corrompus");
        assertFalse(texte.contains("Sanlam"));
        // palette AssurZen, plus de violet, filet orange du pied
        assertTrue(html.contains("#1b3b8f") && html.contains("#14265c") && html.contains("border-top:3px solid #ec7c1e"));
        assertFalse(html.contains("#4f46e5") || html.contains("#7c3aed"));
        assertFalse(html.contains("Accepter ce devis"), "pas de bouton d'acceptation en v1");
    }

    @Test
    void email_sansPdf_neMentionPasLaPieceJointe() {
        assertFalse(HtmlUtils.htmlUnescape(docs.html(moto(), null, "Aïcha", false)).contains("Le devis détaillé est joint"));
    }

    @Test
    void valeursParDefaut_dansLeCode() {
        assertEquals("AssurZen Assurance", QuoteDocumentService.MARQUE_DEFAUT);
        assertEquals("Votre sérénité, notre priorité.", QuoteDocumentService.SLOGAN_DEFAUT);
        assertEquals("Une offre AssurZen Côte d'Ivoire\nBesoin d'aide ? Répondez à cet e-mail ou appelez le service client.", QuoteDocumentService.PIED_DEFAUT);
        assertEquals("customer@assurzen.ci", docs.adresseExpedition());
    }

    private String texte(byte[] pdf, int page) throws Exception {
        return new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(page);
    }

    @Test
    void pdf_vtcAvecRemise_gabaritValide() throws Exception {
        byte[] pdf = docs.pdf(vtc(), "Aïcha K.");
        assertEquals("%PDF", new String(pdf, 0, 4));
        assertEquals(1, new PdfReader(pdf).getNumberOfPages(), "une seule page");
        String t = texte(pdf, 1);
        for (String attendu : new String[]{"AssurZen Assurance", "Votre sérénité, notre priorité.", "DEVIS N°", "DEV-2026-000021", "Calculé", "Assurance automobile VTC",
                "PROSPECT", "VÉHICULE", "Usage VTC", "Déclaré", "Total à payer", "Période de garantie", "DÉTAIL DU PRIX", "Remise 5 %", "CEDEAO", "Attestation",
                "Taxes", "GARANTIES INCLUSES", "Responsabilité civile", "Bon pour accord du client", "Aïcha K., conseiller AssurZen",
                "Une offre AssurZen Côte d'Ivoire", "Besoin d'aide ? Répondez à cet e-mail ou appelez le service client.", "Page 1 / 1"}) {
            assertTrue(t.contains(attendu), "PDF VTC : « " + attendu + " » manquant");
        }
        assertFalse(t.contains("?te"), "accents non corrompus");
    }

    @Test
    void pdf_moto_gabaritValide() throws Exception {
        byte[] pdf = docs.pdf(moto(), null);
        assertEquals(1, new PdfReader(pdf).getNumberOfPages());
        String t = texte(pdf, 1);
        for (String attendu : new String[]{"Assurance moto", "Accepté", "Cylindrée", "N° de châssis", "Prime nette", "Accessoires", "FGA", "Total à payer",
                "Individuel conducteur", "Établi par", "Page 1 / 1"}) {
            assertTrue(t.contains(attendu), "PDF moto : « " + attendu + " » manquant");
        }
        assertFalse(t.contains("CEDEAO") || t.contains("Attestation"), "moto : ni CEDEAO ni attestation");
    }
}
