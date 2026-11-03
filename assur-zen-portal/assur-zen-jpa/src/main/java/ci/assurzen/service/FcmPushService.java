package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.MobileDevice;
import ci.assurzen.repositories.MobileDeviceRepository;
import ci.assurzen.repositories.MobileNotificationPreferenceRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications push de l'application mobile via Firebase Cloud Messaging (API HTTP v1), 2026-11-03. Aucun SDK : le jeton
 * OAuth2 est obtenu avec le compte de service (JWT RS256, scope firebase.messaging) puis les messages sont postes sur
 * https://fcm.googleapis.com/v1/projects/{projet}/messages:send.
 *
 * Securite : le fichier du compte de service reste HORS du depot (firebase.credentials-path, variable
 * FIREBASE_CREDENTIALS_PATH) ; ni la cle privee, ni les jetons d'appareil, ni le jeton OAuth ne sont journalises. L'envoi reel
 * est coupe tant que firebase.push-enabled (FIREBASE_PUSH_ENABLED) n'est pas true. Un envoi ne fait jamais echouer l'action
 * metier qui l'a declenche. Un jeton refuse comme inconnu (UNREGISTERED) desactive l'appareil.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushService {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final OkHttpClient okHttpClient;
    private final MobileDeviceRepository deviceRepository;
    private final MobileNotificationPreferenceRepository preferenceRepository;

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    private final ci.assurzen.services.DynamicConfigurationService configuration;

    private volatile Compte compte;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.EPOCH;

    private record Compte(String projectId, String clientEmail, String tokenUri, PrivateKey cle) {
    }

    public boolean estConfigure() {
        return StringUtils.hasText(credentialsPath) && Files.isReadable(Path.of(credentialsPath));
    }

    /** Envoi reel actif : cle de configuration PUSH_ENABLED (ecran de configuration, sans redemarrage) ET fichier du compte de service lisible. */
    public boolean pushActive() {
        return "true".equalsIgnoreCase(configuration.getString("PUSH_ENABLED", "false"));
    }

    public boolean estActif() {
        return pushActive() && estConfigure();
    }

    /** Diffuse la notification aux appareils actifs du compte si la categorie est activee et le push actif. */
    @Async("commissionPaiementTaskExecutor")
    public void diffuser(Long userId, String categorie, String titre, String corps, Long notificationId, Long contractId, Long claimId) {
        if (!estActif()) {
            return;
        }
        try {
            boolean desactivee = preferenceRepository.findByUserIdAndCategory(userId, categorie).map(p -> !p.isEnabled()).orElse(false);
            if (desactivee) {
                return;
            }
            Map<String, String> donnees = new LinkedHashMap<>();
            donnees.put("category", categorie);
            if (notificationId != null) {
                donnees.put("notificationId", String.valueOf(notificationId));
            }
            if (contractId != null) {
                donnees.put("contractId", String.valueOf(contractId));
            }
            if (claimId != null) {
                donnees.put("claimId", String.valueOf(claimId));
            }
            List<MobileDevice> appareils = deviceRepository.findByUserIdAndActiveTrue(userId);
            for (MobileDevice appareil : appareils) {
                Resultat resultat = envoyer(appareil.getToken(), titre, corps, donnees, false);
                if (resultat == Resultat.JETON_INCONNU) {
                    appareil.setActive(false);
                    deviceRepository.save(appareil);
                    log.info("[PUSH] appareil {} desactive (jeton inconnu de FCM)", appareil.getId());
                }
            }
        } catch (RuntimeException e) {
            log.warn("[PUSH] diffusion impossible (utilisateur {}) : {}", userId, e.getMessage());
        }
    }

    /** Verifie la configuration : lecture du compte de service et authentification Google (sans envoyer de message). */
    public MobileExtraDtos.PushStatus verifier() {
        if (!estConfigure()) {
            return new MobileExtraDtos.PushStatus(false, false, null, false,
                    "Fichier du compte de service introuvable (FIREBASE_CREDENTIALS_PATH).");
        }
        try {
            Compte c = chargerCompte();
            jeton();
            // validate_only avec un jeton factice : une reponse 400 INVALID_ARGUMENT prouve que l'authentification et le projet sont corrects
            Resultat r = envoyer("jeton-de-verification-invalide", "verification", "verification", Map.of(), true);
            boolean ok = r != Resultat.ERREUR_AUTH && r != Resultat.ERREUR;
            return new MobileExtraDtos.PushStatus(true, pushActive(), c.projectId(), ok,
                    ok ? (pushActive() ? "Configuration valide, envoi actif." : "Configuration valide, envoi COUPE (cle PUSH_ENABLED a false).")
                            : "Authentification ou projet refuse par Google : verifier le compte de service et l'API Firebase Cloud Messaging V1.");
        } catch (Exception e) {
            return new MobileExtraDtos.PushStatus(true, pushActive(), null, false, "Erreur de configuration : " + e.getClass().getSimpleName());
        }
    }

    enum Resultat { OK, JETON_INCONNU, ERREUR_AUTH, ERREUR }

    Resultat envoyer(String jetonAppareil, String titre, String corps, Map<String, String> donnees, boolean validerSeulement) {
        try {
            Compte c = chargerCompte();
            JsonObject notification = new JsonObject();
            notification.addProperty("title", titre);
            notification.addProperty("body", corps);
            JsonObject message = new JsonObject();
            message.addProperty("token", jetonAppareil);
            message.add("notification", notification);
            message.add("data", GSON.toJsonTree(donnees));
            JsonObject android = new JsonObject();
            android.addProperty("priority", "HIGH");
            message.add("android", android);
            JsonObject corpsRequete = new JsonObject();
            corpsRequete.addProperty("validate_only", validerSeulement);
            corpsRequete.add("message", message);

            Request requete = new Request.Builder()
                    .url("https://fcm.googleapis.com/v1/projects/" + c.projectId() + "/messages:send")
                    .header("Authorization", "Bearer " + jeton())
                    .post(RequestBody.create(corpsRequete.toString(), JSON))
                    .build();
            try (Response reponse = okHttpClient.newCall(requete).execute()) {
                String texte = reponse.body() == null ? "" : reponse.body().string();
                if (reponse.isSuccessful()) {
                    return Resultat.OK;
                }
                if (reponse.code() == 401 || reponse.code() == 403) {
                    accessToken = null;
                    log.warn("[PUSH] FCM refuse l'authentification ({})", reponse.code());
                    return Resultat.ERREUR_AUTH;
                }
                if (texte.contains("UNREGISTERED") || reponse.code() == 404
                        || (reponse.code() == 400 && texte.contains("INVALID_ARGUMENT"))) {
                    return Resultat.JETON_INCONNU;
                }
                log.warn("[PUSH] echec FCM {}", reponse.code());
                return Resultat.ERREUR;
            }
        } catch (Exception e) {
            log.warn("[PUSH] erreur d'envoi : {}", e.getClass().getSimpleName());
            return Resultat.ERREUR;
        }
    }

    // ------------------------------------------------------------------ authentification Google

    private synchronized Compte chargerCompte() throws Exception {
        if (compte == null) {
            JsonObject json = JsonParser.parseString(Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8)).getAsJsonObject();
            String pem = json.get("private_key").getAsString()
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey cle = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            compte = new Compte(json.get("project_id").getAsString(), json.get("client_email").getAsString(),
                    json.has("token_uri") ? json.get("token_uri").getAsString() : "https://oauth2.googleapis.com/token", cle);
        }
        return compte;
    }

    private synchronized String jeton() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(120))) {
            return accessToken;
        }
        Compte c = chargerCompte();
        long maintenant = Instant.now().getEpochSecond();
        JsonObject entete = new JsonObject();
        entete.addProperty("alg", "RS256");
        entete.addProperty("typ", "JWT");
        JsonObject revendications = new JsonObject();
        revendications.addProperty("iss", c.clientEmail());
        revendications.addProperty("scope", SCOPE);
        revendications.addProperty("aud", c.tokenUri());
        revendications.addProperty("iat", maintenant);
        revendications.addProperty("exp", maintenant + 3600);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String aSigner = b64.encodeToString(entete.toString().getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(revendications.toString().getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(c.cle());
        signature.update(aSigner.getBytes(StandardCharsets.UTF_8));
        String assertion = aSigner + "." + b64.encodeToString(signature.sign());

        Request requete = new Request.Builder().url(c.tokenUri())
                .post(new FormBody.Builder().add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer").add("assertion", assertion).build())
                .build();
        try (Response reponse = okHttpClient.newCall(requete).execute()) {
            String texte = reponse.body() == null ? "" : reponse.body().string();
            if (!reponse.isSuccessful()) {
                throw new IllegalStateException("authentification Google refusee (" + reponse.code() + ")");
            }
            JsonObject json = JsonParser.parseString(texte).getAsJsonObject();
            accessToken = json.get("access_token").getAsString();
            accessTokenExpiry = Instant.now().plusSeconds(json.has("expires_in") ? json.get("expires_in").getAsLong() : 3000);
            return accessToken;
        }
    }
}
