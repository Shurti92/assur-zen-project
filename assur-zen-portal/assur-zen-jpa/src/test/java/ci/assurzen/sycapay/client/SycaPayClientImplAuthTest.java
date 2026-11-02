package ci.assurzen.sycapay.client;

import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Authentification SycaPay : chaque cause d'echec est distinguee (incident prod du 2026-10-05). */
class SycaPayClientImplAuthTest {

    private static SycaPayClientImpl client(Interceptor interceptor) {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setBaseUrl("https://sycapay.invalid/delivery/v1");
        properties.setMerchantId("C_MERCHANT_TEST");
        properties.setApiKey("pk_test_non_secret");
        properties.setSecretKey("sk_test_non_secret");
        return new SycaPayClientImpl(new OkHttpClient.Builder().addInterceptor(interceptor).build(), properties);
    }

    private static Interceptor reponse(int code, String body, String contentType) {
        return chain -> new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(ResponseBody.create(body, MediaType.parse(contentType))).build();
    }

    @Test
    void reponseHtml_messageNonJsonAvecStatutHttp() {
        SycaPayAuthenticationException ex = assertThrows(SycaPayAuthenticationException.class,
                () -> client(reponse(403, "<html><body>Forbidden</body></html>", "text/html")).authenticate());
        assertEquals("Reponse d'authentification SycaPay non JSON (HTTP 403)", ex.getMessage());
    }

    @Test
    void erreurReseau_messageReseau() {
        SycaPayAuthenticationException ex = assertThrows(SycaPayAuthenticationException.class,
                () -> client(chain -> { throw new IOException("timeout"); }).authenticate());
        assertTrue(ex.getMessage().contains("reseau ou delai depasse"));
    }

    @Test
    void reponseJson_lueNormalement() {
        assertEquals(0, client(reponse(200, "{\"code\":0,\"token\":\"t\"}", "application/json")).authenticate().code());
    }

    @Test
    void extrait_aplatitEtTronque() {
        assertEquals("a b", SycaPayClientImpl.extrait("a\n  b"));
        assertEquals(303, SycaPayClientImpl.extrait("x".repeat(500)).length());
    }
}
