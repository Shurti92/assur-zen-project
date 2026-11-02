package ci.assurzen.sycapay.client;

import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayTransferRequest;
import ci.assurzen.sycapay.dto.SycaPayTransferResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requete REELLEMENT construite par SycaPayClientImpl pour POST /checktransferwave (capturee par un
 * intercepteur OkHttp, aucun appel reseau) : l'en-tete AUTHORIZATION doit etre le HMAC calcule sur le
 * MEME montant que celui du corps JSON, recalcule a chaque paiement (confirmation SycaPay du
 * 2026-09-23 : data = montant + merchantId + secretKey ; hash_hmac('sha256', data, secretKey), hex).
 */
class SycaPayClientImplTransferTest {

    private static final String MERCHANT_ID = "C_MERCHANT_TEST";
    private static final String SECRET_KEY = "sk_test_non_secret";

    private final AtomicReference<Request> requeteCapturee = new AtomicReference<>();

    private SycaPayClientImpl client() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setBaseUrl("https://sycapay.invalid/delivery/v1");
        properties.setMerchantId(MERCHANT_ID);
        properties.setApiKey("pk_test_non_secret");
        properties.setSecretKey(SECRET_KEY);
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requeteCapturee.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("{\"code\":0,\"status\":\"pending\",\"description\":\"EN COURS\"}",
                                    MediaType.parse("application/json")))
                            .build();
                })
                .build();
        return new SycaPayClientImpl(http, properties);
    }

    private static SycaPayTransferRequest transfert(String montant) {
        return new SycaPayTransferRequest("SYC2026000100", "1", "Commission partenaire", "AssurZen", "AssurZen",
                "Beneficiaire test", montant, "XOF", "fr", "https://cancel.invalid", "https://success.invalid",
                "+2250700000000", "Wave", "Test");
    }

    private JsonNode corpsEnvoye() throws Exception {
        Buffer buffer = new Buffer();
        requeteCapturee.get().body().writeTo(buffer);
        return new ObjectMapper().readTree(buffer.readUtf8());
    }

    @Test
    void authorization_estLeHmacDuMontantEffectivementEnvoyeDansLeCorps() throws Exception {
        SycaPayTransferResponse response = client().transferWave("tok-1", transfert("100"));

        Request requete = requeteCapturee.get();
        assertEquals("https://sycapay.invalid/delivery/v1/checktransferwave", requete.url().toString());
        String montantDuCorps = corpsEnvoye().get("amount").asText();
        assertEquals("100", montantDuCorps);
        assertEquals(SycaPayHmacSigner.sign(montantDuCorps, MERCHANT_ID, SECRET_KEY), requete.header("AUTHORIZATION"));
        assertEquals(0, response.code());
    }

    /** Montant tel quel, sans reformatage (jamais "25000.00" si le corps porte "25000"). */
    @Test
    void authorization_suitLeMontant_etEstRecalculeeACHaquePaiement() throws Exception {
        SycaPayClientImpl client = client();

        client.transferWave("tok-1", transfert("25000"));
        String authorization25000 = requeteCapturee.get().header("AUTHORIZATION");
        assertEquals("25000", corpsEnvoye().get("amount").asText());
        assertEquals(SycaPayHmacSigner.sign("25000", MERCHANT_ID, SECRET_KEY), authorization25000);
        assertNotEquals(SycaPayHmacSigner.sign("25000.00", MERCHANT_ID, SECRET_KEY), authorization25000);

        client.transferWave("tok-1", transfert("100"));
        assertNotEquals(authorization25000, requeteCapturee.get().header("AUTHORIZATION"));
    }

    /** En-tetes du contrat checktransfer (PDF p.12) : marchand, token, signature, formats - jamais le secret. */
    @Test
    void entetes_duContratSycaPay_sansJamaisExposerLeSecret() throws Exception {
        client().transferWave("tok-1", transfert("100"));

        Request requete = requeteCapturee.get();
        assertEquals(MERCHANT_ID, requete.header("X-SYCA-MERCHANDID"));
        assertEquals("tok-1", requete.header("TOKEN"));
        assertEquals("JSON", requete.header("X-SYCA-REQUEST-DATA-FORMAT"));
        assertEquals("JSON", requete.header("X-SYCA-RESPONSE-DATA-FORMAT"));
        assertTrue(requete.header("AUTHORIZATION").matches("[0-9a-f]{64}"), "HMAC-SHA256 en hexadecimal minuscule");
        assertNull(requete.header("X-SYCA-SECRETKEY"));
        assertTrue(!requete.headers().toString().contains(SECRET_KEY) && !corpsEnvoye().toString().contains(SECRET_KEY),
                "La secretKey ne doit jamais etre transmise en clair");
    }
}
