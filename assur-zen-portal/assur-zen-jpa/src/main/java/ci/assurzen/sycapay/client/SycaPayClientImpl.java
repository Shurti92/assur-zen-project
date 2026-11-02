package ci.assurzen.sycapay.client;

import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayAuthResponse;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.dto.SycaPayTransferRequest;
import ci.assurzen.sycapay.dto.SycaPayTransferResponse;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Implementation OkHttp du contrat SycaPay. Derive son propre OkHttpClient a partir du bean partage
 * (OkHttpConfig) via newBuilder() : conserve le pool de connexions/l'executor commun tout en
 * appliquant des timeouts dedies (sycapay.connect-timeout-ms / read-timeout-ms), potentiellement
 * differents de ceux du SMS/Telegram qui partagent aussi ce bean.
 *
 * IMPORTANT (cf. section 11 de la specification, transactions "UNKNOWN") : transferWave() et
 * getStatus() retournent null sur toute erreur reseau/timeout au lieu de lever une exception - un
 * appelant qui traiterait une IOException comme un echec definitif du paiement risquerait de relancer
 * un virement deja parti cote SycaPay. C'est aux services appelants (sycapay.service) de traduire ce
 * null en statut UNKNOWN, jamais en FAILED.
 */
@Slf4j
@Component
public class SycaPayClientImpl implements SycaPayClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final SycaPayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SycaPayClientImpl(OkHttpClient sharedOkHttpClient, SycaPayProperties properties) {
        this.properties = properties;
        this.http = sharedOkHttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .writeTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    @Override
    public SycaPayAuthResponse authenticate() {
        String jsonBody = writeJson(new LanguagePayload(properties.getLanguage()));
        Request request = new Request.Builder()
                .url(properties.getBaseUrl() + "/authentification")
                .header("X-SYCA-MERCHANDID", properties.getMerchantId())
                .header("X-SYCA-APIKEY", properties.getApiKey())
                .header("X-SYCA-REQUEST-DATA-FORMAT", "JSON")
                .header("X-SYCA-RESPONSE-DATA-FORMAT", "JSON")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        // Diagnostic (incident prod du 2026-10-05, virements des demandes de paiement) : l'ancienne branche
        // IOException melangeait erreur reseau ET reponse non JSON (JsonProcessingException), sans rien
        // journaliser. Chaque cas est desormais distingue et journalise (statut HTTP + debut de la reponse,
        // jamais les identifiants envoyes).
        Response response;
        try {
            response = http.newCall(request).execute();
        } catch (IOException e) {
            log.warn("[SYCAPAY] authentification : erreur reseau ou delai depasse vers {}/authentification : {} ({})",
                    properties.getBaseUrl(), e.getMessage(), e.getClass().getSimpleName());
            throw new SycaPayAuthenticationException("Echec technique de l'appel d'authentification SycaPay (reseau ou delai depasse)", e);
        }
        try (response) {
            String body = readBody(response);
            if (body == null || body.isBlank()) {
                log.warn("[SYCAPAY] authentification : reponse vide (HTTP {})", response.code());
                throw new SycaPayAuthenticationException("Reponse vide de SycaPay lors de l'authentification (HTTP " + response.code() + ")");
            }
            try {
                return objectMapper.readValue(body, SycaPayAuthResponse.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("[SYCAPAY] authentification : reponse non JSON (HTTP {}, Content-Type {}) : {}",
                        response.code(), response.header("Content-Type"), extrait(body));
                throw new SycaPayAuthenticationException("Reponse d'authentification SycaPay non JSON (HTTP " + response.code() + ")", e);
            }
        } catch (IOException e) {
            log.warn("[SYCAPAY] authentification : lecture de la reponse impossible : {}", e.getMessage());
            throw new SycaPayAuthenticationException("Echec technique de l'appel d'authentification SycaPay (lecture de la reponse)", e);
        } catch (SycaPayAuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SycaPayAuthenticationException("Reponse d'authentification SycaPay illisible", e);
        }
    }

    @Override
    public SycaPayBalanceResponse checkBalance() {
        // Corps vide en x-www-form-urlencoded (fidele a la collection Postman : body.mode="urlencoded"
        // avec un tableau de parametres vide) - un simple RequestBody.create(new byte[0], null) omettrait
        // le Content-Type attendu par certains serveurs pour une requete POST avec corps vide.
        Request request = new Request.Builder()
                .url(properties.getBaseUrl() + "/checkcashoutsolde.php")
                .header("X-Syca-Merchandid", properties.getMerchantId())
                .post(new FormBody.Builder().build())
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = readBody(response);
            if (body == null || body.isBlank()) {
                throw new SycaPayBalanceException("Reponse vide de SycaPay lors de la consultation du solde (HTTP " + response.code() + ")");
            }
            return objectMapper.readValue(body, SycaPayBalanceResponse.class);
        } catch (IOException e) {
            throw new SycaPayBalanceException("Echec technique de l'appel de consultation du solde SycaPay", e);
        } catch (SycaPayBalanceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SycaPayBalanceException("Reponse de solde SycaPay illisible", e);
        }
    }

    @Override
    public SycaPayTransferResponse transferWave(String token, SycaPayTransferRequest transferRequest) {
        String authorization = SycaPayHmacSigner.sign(transferRequest.amount(), properties.getMerchantId(), properties.getSecretKey());
        String jsonBody = writeJson(transferRequest);

        Request request = new Request.Builder()
                .url(properties.getBaseUrl() + "/checktransferwave")
                .header("X-SYCA-MERCHANDID", properties.getMerchantId())
                .header("TOKEN", token)
                .header("AUTHORIZATION", authorization)
                .header("X-SYCA-REQUEST-DATA-FORMAT", "JSON")
                .header("X-SYCA-RESPONSE-DATA-FORMAT", "JSON")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = readBody(response);
            if (body == null || body.isBlank()) {
                log.warn("[SYCAPAY] reponse vide sur checktransferwave (HTTP {}) - orderid={} traite comme resultat INCONNU",
                        response.code(), transferRequest.orderid());
                return null;
            }
            return objectMapper.readValue(body, SycaPayTransferResponse.class);
        } catch (IOException e) {
            log.warn("[SYCAPAY] erreur reseau/timeout sur checktransferwave - orderid={} traite comme resultat INCONNU (jamais reessaye automatiquement) : {}",
                    transferRequest.orderid(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("[SYCAPAY] reponse illisible sur checktransferwave - orderid={} traite comme resultat INCONNU : {}",
                    transferRequest.orderid(), e.getMessage());
            return null;
        }
    }

    @Override
    public SycaPayStatusResponse getStatus(String orderId) {
        RequestBody form = new FormBody.Builder()
                .add("reference", orderId)
                .add("merchantid", properties.getMerchantId())
                .build();

        Request request = new Request.Builder()
                .url(properties.getBaseUrl() + "/getStatusByOrderID.php")
                .post(form)
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = readBody(response);
            if (body == null || body.isBlank()) {
                log.warn("[SYCAPAY] reponse vide sur getStatusByOrderID (HTTP {}) - orderid={}", response.code(), orderId);
                return null;
            }
            return objectMapper.readValue(body, SycaPayStatusResponse.class);
        } catch (IOException e) {
            log.warn("[SYCAPAY] erreur reseau/timeout sur getStatusByOrderID - orderid={} : {}", orderId, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("[SYCAPAY] reponse illisible sur getStatusByOrderID - orderid={} : {}", orderId, e.getMessage());
            return null;
        }
    }

    /** Debut d'une reponse pour les journaux : 300 caracteres, sauts de ligne aplatis. */
    static String extrait(String body) {
        String plat = body.replaceAll("\\s+", " ").trim();
        return plat.length() > 300 ? plat.substring(0, 300) + "..." : plat;
    }

    private String readBody(Response response) throws IOException {
        return response.body() != null ? response.body().string() : null;
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Echec de serialisation JSON d'une requete SycaPay", e);
        }
    }

    private record LanguagePayload(String language) {
    }
}
