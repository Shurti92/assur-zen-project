package ci.assurzen.services;

import ci.assurzen.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications Telegram (canal choisi par le produit le 2026-08-23, remplace un besoin de
 * notification qui aurait sinon exige un fournisseur SMS payant supplementaire). Best-effort : un
 * echec d'envoi est logge mais ne doit jamais faire echouer le flux metier qui l'a declenche (la
 * demande de paiement partiel reste creee meme si Telegram est injoignable) - execution async pour
 * ne pas ralentir la reponse HTTP au partenaire.
 *
 * chatId supporte plusieurs destinataires separes par une virgule (ex. "123456789,123456789") -
 * ajout du 2026-08-23 : chaque destinataire recoit un envoi independant, l'echec de l'un
 * n'empeche pas les autres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** A appliquer sur tout segment dynamique (nom, telephone, reference...) insere dans un texte
     * envoye avec parse_mode=HTML - Telegram rejette (ou tronque) le message entier si &lt;, &gt; ou &amp;
     * non-echappes s'y trouvent, ex. un nom de partenaire contenant "&amp;" ou "&lt;". Ne PAS appliquer
     * au texte final deja compose (qui contient volontairement les vraies balises &lt;b&gt;). */
    public static String echapperHtml(String texte) {
        if (texte == null) {
            return null;
        }
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Async("telegramTaskExecutor")
    public void envoyer(String texte) {
        if (!telegramProperties.isEnabled()) {
            log.debug("Notification Telegram desactivee (credential.telegram.enabled=false), envoi ignore");
            return;
        }
        if (!StringUtils.hasText(telegramProperties.getBotToken()) || !StringUtils.hasText(telegramProperties.getChatId())) {
            log.warn("Notification Telegram activee mais botToken/chatId manquant, envoi ignore");
            return;
        }

        List<String> chatIds = Arrays.stream(telegramProperties.getChatId().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        for (String chatId : chatIds) {
            envoyerA(chatId, texte);
        }
    }

    private void envoyerA(String chatId, String texte) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", texte);
            // HTML plutot que MarkdownV2 (retour utilisateur, 2026-08-30) : autorise <b>gras</b> sans
            // devoir echapper tous les caracteres speciaux MarkdownV2 (., -, (, ) etc., frequents dans
            // un numero de telephone ou une reference) - un seul caractere non echappe y ferait echouer
            // tout l'envoi.
            payload.put("parse_mode", "HTML");
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), JSON);

            Request request = new Request.Builder()
                    .url("https://api.telegram.org/bot" + telegramProperties.getBotToken() + "/sendMessage")
                    .post(body)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Echec de l'envoi de la notification Telegram au chat_id {}, statut HTTP {}", chatId, response.code());
                }
            }
        } catch (IOException e) {
            log.error("Erreur technique lors de l'envoi de la notification Telegram au chat_id {}", chatId, e);
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'envoi de la notification Telegram au chat_id {}", chatId, e);
        }
    }
}
