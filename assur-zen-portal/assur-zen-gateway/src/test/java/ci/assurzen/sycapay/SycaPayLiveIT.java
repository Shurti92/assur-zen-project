package ci.assurzen.sycapay;

import ci.assurzen.RootAssurZen;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayAuthResponse;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;

/**
 * Test MANUEL contre l'API SycaPay REELLE (production : aucune sandbox connue au 2026-09-23).
 * Jamais execute par un "mvn test" normal :
 * - SYCAPAY_LIVE_TEST=true  : etapes sans argent engage (authentification, solde) ;
 * - SYCAPAY_LIVE_TRANSFER=true en plus : VIREMENT WAVE REEL de 100 XOF vers le numero de test, puis
 *   verification du statut aupres de SycaPay et en base (montant/numero de test du 2026-09-23).
 * Credentials lus dans SYCAPAY_MERCHANT_ID / SYCAPAY_API_KEY / SYCAPAY_SECRET_KEY (jamais en dur).
 * Profil dev (base locale). SMS/mail/Telegram neutralises. SYCAPAY_ENABLED reste false en base :
 * seul ce test le force a true, sur son propre contexte Spring.
 */
@SpringBootTest(classes = RootAssurZen.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "credential.sms.base-url=http://127.0.0.1:9/sms-bloque",
        "credential.telegram.enabled=false",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=9"
})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SYCAPAY_LIVE_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SycaPayLiveIT {

    private static final long MONTANT_TEST = 100L;
    private static final String NUMERO_TEST = "0757873487";

    /** Etat partage entre les etapes ordonnees (un seul virement par execution). */
    private static String transactionReference;

    @MockitoSpyBean
    private SycaPayProperties properties;

    @Autowired
    private SycaPayClient sycaPayClient;

    @Autowired
    private SycaPayPaymentService paymentService;

    @Autowired
    private SycaPayTransactionRepository repository;

    @BeforeEach
    void activerSycaPayPourCeTestUniquement() {
        doReturn(true).when(properties).isEnabled();
    }

    @Test
    @Order(1)
    void etape1_authentification() {
        SycaPayAuthResponse response = sycaPayClient.authenticate();

        System.out.println("[LIVE] authentification : code=" + response.code() + ", description=" + response.description()
                + ", token recu=" + (response.token() != null && !response.token().isBlank()));
        assertTrue(response.isSuccess(), "Authentification SycaPay refusee : " + response.description());
    }

    @Test
    @Order(2)
    void etape2_solde() {
        SycaPayBalanceResponse response = sycaPayClient.checkBalance();

        assertNotNull(response);
        System.out.println("[LIVE] solde : " + response.description());
        if (response.resultats() != null) {
            response.resultats().forEach(e -> System.out.println("[LIVE]   " + e.operateur() + " = " + e.solde()));
        }
    }

    @Test
    @Order(3)
    void etape3_virementReel() {
        assumeTrue("true".equals(System.getenv("SYCAPAY_LIVE_TRANSFER")),
                "SYCAPAY_LIVE_TRANSFER!=true : virement reel non demande, etape ignoree");

        // contractId/commercialId negatifs : aucune ligne metier reelle (pas de FK sur la table), et
        // unique par execution pour ne pas buter sur la garde d'idempotence d'un essai precedent.
        long idTest = -(System.currentTimeMillis() / 1000);
        SycaPayPaymentOutcome outcome = paymentService.payCommission(
                new SycaPayPaymentCommand(idTest, idTest, MONTANT_TEST, "Test integration AssurZen", NUMERO_TEST));

        System.out.println("[LIVE] virement : rejete=" + outcome.rejected() + ", statut=" + outcome.status()
                + ", reference=" + outcome.transactionReference() + ", message=" + outcome.message());
        assertFalse(outcome.rejected(), "Paiement rejete avant tout appel : " + outcome.message());
        assertNotNull(outcome.transactionReference());
        transactionReference = outcome.transactionReference();

        SycaPayTransaction enBase = repository.findByTransactionReference(transactionReference).orElseThrow();
        System.out.println("[LIVE] en base : statut=" + enBase.getStatus() + ", montant=" + enBase.getAmount()
                + ", telephone=" + enBase.getBeneficiaryPhone() + ", reponse SycaPay=" + enBase.getSycapayResponseCode()
                + " / " + enBase.getSycapayResponseMessage());
        assertEquals(outcome.status(), enBase.getStatus());
        assertEquals(MONTANT_TEST, enBase.getAmount());

        String merchant = properties.getMerchantId();
        System.out.println("[LIVE] ===== Resume technique (aucun secret) =====");
        System.out.println("[LIVE] Montant envoye     : " + enBase.getAmount());
        System.out.println("[LIVE] Beneficiaire       : " + enBase.getBeneficiaryPhone());
        System.out.println("[LIVE] MerchantID utilise : " + (merchant == null || merchant.length() < 6 ? "***"
                : merchant.substring(0, 4) + "***" + merchant.substring(merchant.length() - 3)));
        System.out.println("[LIVE] HMAC genere        : oui (non affiche)");
        System.out.println("[LIVE] Requete envoyee    : checktransferwave");
        System.out.println("[LIVE] Reponse SycaPay    : code=" + enBase.getSycapayResponseCode() + " / " + enBase.getSycapayResponseMessage());
        System.out.println("[LIVE] Statut             : " + enBase.getStatus());
    }

    @Test
    @Order(4)
    void etape4_statutAupresDeSycaPay() {
        assumeTrue(transactionReference != null, "Pas de virement effectue dans cette execution");

        SycaPayStatusResponse response = sycaPayClient.getStatus(transactionReference);

        System.out.println("[LIVE] getStatusByOrderID(" + transactionReference + ") : "
                + (response == null ? "aucune reponse exploitable" : response));
        assertNotNull(response, "Aucune reponse de getStatusByOrderID");
    }
}
