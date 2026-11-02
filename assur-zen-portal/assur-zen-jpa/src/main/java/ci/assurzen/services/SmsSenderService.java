package ci.assurzen.services;

import ci.assurzen.config.SmsProperties;
import ci.assurzen.dto.MoEnvoiMessage;
import ci.assurzen.dto.SmsSendResult;
import ci.assurzen.exception.SmsSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsSenderService {

    private static final String DLR_CALLBACK_URL = "https://mydomain.com:4444/dlr";

    private final OkHttpClient okHttpClient;
    private final SmsProperties smsProperties;

    public SmsSendResult envoiMessageAuNumero(MoEnvoiMessage sms) {

        log.info("Envoi SMS demande pour le numero se terminant par {}", masquerNumero(sms.getNumero()));

        if (smsProperties.isMock()) {
            log.warn("SMS MOCK (aucun appel fournisseur) - numero {} - message : {}",
                    masquerNumero(sms.getNumero()), sms.getMessage());
            return new SmsSendResult(true, 200, "mock");
        }

        HttpUrl url = construireUrl(sms);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.warn("Echec de l'envoi SMS, statut HTTP {}", response.code());
            }

            return new SmsSendResult(response.isSuccessful(), response.code(), body);
        } catch (IOException ex) {
            log.error("Erreur technique lors de l'envoi du SMS", ex);
            throw new SmsSendException("Echec de l'appel au fournisseur SMS", ex);
        }
    }

    private HttpUrl construireUrl(MoEnvoiMessage sms) {
        HttpUrl base = Objects.requireNonNull(
                HttpUrl.parse(smsProperties.getBaseUrl()),
                "URL de base SMS invalide (verifier la configuration)"
        );

        return base.newBuilder()
                .addPathSegment("send")
                .addQueryParameter("from", smsProperties.getSender())
                .addQueryParameter("to", normaliserAuFormatInternational(sms.getNumero()))
                .addQueryParameter("content", sms.getMessage())
                .addQueryParameter("token", smsProperties.getBearer())
                .addQueryParameter("dlrUrl", DLR_CALLBACK_URL)
                .addQueryParameter("dlrMethod", "GET")
                .addQueryParameter("customData", "customData")
                .addQueryParameter("sendAt", OffsetDateTime.now().toString())
                .build();
    }

    /**
     * LETEXTO exige un numero au format international. Le format ivoirien issu de la
     * renumerotation de 2021 conserve le 0 initial dans le numero d'abonne meme a
     * l'international (ex: 0700000000 -> +2250700000000), il ne faut donc pas le retirer.
     */
    private String normaliserAuFormatInternational(String numero) {
        if (numero == null) {
            return null;
        }
        String trimmed = numero.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("00")) {
            return "+" + trimmed.substring(2);
        }
        if (trimmed.startsWith("225")) {
            return "+" + trimmed;
        }
        if (trimmed.matches("0\\d{9}")) {
            return "+225" + trimmed;
        }
        log.warn("Numero de telephone dans un format non reconnu, envoi sans normalisation: {}", masquerNumero(trimmed));
        return trimmed;
    }

    private String masquerNumero(String numero) {
        if (numero == null || numero.length() < 4) {
            return "****";
        }
        return "****" + numero.substring(numero.length() - 4);
    }
}
