package ci.assurzen.security;

import ci.assurzen.entities.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final Gson GSON = new Gson();

    /** Emetteur pose sur chaque nouveau jeton (defense en profondeur si un autre service en venait
     * a partager le meme secret). Verifie en validation UNIQUEMENT quand present (cf. parseAndValidate) :
     * les jetons emis avant l'ajout de ce claim (refresh token jusqu'a 7 jours de duree de vie) n'en
     * disposent pas encore et ne doivent pas etre rejetes en masse au deploiement de ce changement. */
    private static final String ISSUER = "assur-zen-gateway";

    private final TokenRevocationService tokenRevocationService;
    private final UserActiveStatusCache userActiveStatusCache;

    @Value("${app.jwt.secret}")
    private String secret;

    // Repli statique si SESSION_TIMEOUT (Dynamic Configuration Engine) est absent/inactif en base -
    // resolu par l'appelant (AuthService) et passe explicitement a generateAccessToken(user, ttl),
    // PAS lu directement ici : injecter DynamicConfigurationService dans ce service creerait un
    // cycle (JwtTokenService -> DynamicConfigurationService -> AuthService -> JwtTokenService).
    @Value("${app.jwt.access-ttl-seconds:3600}")
    private long accessTtlSeconds;

    @Value("${app.jwt.refresh-ttl-seconds:604800}")
    private long refreshTtlSeconds;

    @Value("${app.jwt.otp-challenge-ttl-seconds:300}")
    private long otpChallengeTtlSeconds;

    public String generateAccessToken(User user) {
        return generateToken(user, "access", accessTtlSeconds, Map.of());
    }

    /** Variante utilisee par AuthService quand SESSION_TIMEOUT (Dynamic Configuration Engine) est actif. */
    public String generateAccessToken(User user, long ttlSecondsOverride) {
        return generateToken(user, "access", ttlSecondsOverride, Map.of());
    }

    /** Access token rattache a une session (claim "sid", 2026-10-03) : permet de reconnaitre la session
     * courante dans la liste des appareils connectes. */
    public String generateAccessToken(User user, long ttlSecondsOverride, String sessionId) {
        return generateToken(user, "access", ttlSecondsOverride, sessionId == null ? Map.of() : Map.of("sid", sessionId));
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, "refresh", refreshTtlSeconds, Map.of());
    }

    /**
     * Jeton de courte duree liant une tentative de connexion (identifiants deja valides)
     * a un numero de telephone, en attente de la verification du code OTP (2FA).
     */
    public String generateOtpChallengeToken(User user, String phone) {
        return generateToken(user, "otp_challenge", otpChallengeTtlSeconds, Map.of("phone", phone));
    }

    /** Duree de validite du jeton de verification de numero (mobile, 2026-11-02). */
    public static final long VERIFICATION_TTL_SECONDS = 600;

    /**
     * Jeton prouvant la possession d'un numero apres verification d'un code SMS (inscription mobile) :
     * sub = 0 (aucun utilisateur), username = telephone, roles = objet (REGISTER ou RESET_PIN), usage unique
     * (jti revoque a la consommation, cf. consommerJetonVerification).
     */
    public String generateVerificationToken(String phone, String purpose) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", "0");
        payload.put("username", phone);
        payload.put("roles", purpose);
        payload.put("typ", "phone_verification");
        payload.put("iat", now);
        payload.put("exp", now + VERIFICATION_TTL_SECONDS);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iss", ISSUER);
        String header = base64Url(GSON.toJson(Map.of("alg", "HS256", "typ", "JWT")));
        String body = base64Url(GSON.toJson(payload));
        return header + "." + body + "." + sign(header + "." + body);
    }

    /**
     * Valide le jeton (signature, expiration, type, numero, objet) puis le consomme : un second usage est
     * refuse. Retourne false si le jeton est invalide, expire, deja utilise ou pour un autre numero/objet.
     */
    public boolean consommerJetonVerification(String token, String phone, String purpose) {
        try {
            JwtClaims claims = parseAndValidate(token);
            if (!"phone_verification".equals(claims.type()) || !phone.equals(claims.username())
                    || !purpose.equals(claims.roles()) || tokenRevocationService.estRevoque(claims.tokenId())) {
                return false;
            }
            tokenRevocationService.revoquer(claims.tokenId(), claims.expiresAt());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public long getOtpChallengeTtlSeconds() {
        return otpChallengeTtlSeconds;
    }

    public JwtClaims parseAndValidate(String token) {
        String[] parts = splitToken(token);
        String signedPayload = parts[0] + "." + parts[1];
        String expectedSignature = sign(signedPayload);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        long exp = requireLongClaim(payload, "exp");
        requireClaim(payload, "sub");
        requireClaim(payload, "typ");
        requireClaim(payload, "jti");
        // "iss" verifie seulement s'il est present (cf. javadoc de ISSUER) : absence tolerée
        // pendant la periode de transition, valeur incorrecte toujours rejetee.
        Object iss = payload.get("iss");
        if (iss != null && !ISSUER.equals(String.valueOf(iss))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
        if (Instant.now().getEpochSecond() >= exp) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton expiré");
        }

        return new JwtClaims(
                Long.valueOf(String.valueOf(payload.get("sub"))),
                String.valueOf(payload.get("username")),
                String.valueOf(payload.get("typ")),
                String.valueOf(payload.get("jti")),
                String.valueOf(payload.get("roles")),
                payload.get("phone") == null ? null : String.valueOf(payload.get("phone")),
                Instant.ofEpochSecond(exp),
                payload.get("sid") == null ? null : String.valueOf(payload.get("sid"))
        );
    }

    public boolean isAccessToken(String token) {
        return "access".equals(parseAndValidate(token).type());
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseAndValidate(token).type());
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public AuthenticatedUserPrincipal toPrincipal(String token) {
        JwtClaims claims = parseAndValidate(token);
        if (!"access".equals(claims.type())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton d'accès invalide");
        }
        // Revocation (audit de securite pre-production, 2026-08-23) : un access token deja
        // logout/mot de passe change ne doit plus etre accepte, meme si sa signature et son
        // expiration naturelle restent valides. Uniquement les access tokens - le refresh token a
        // deja son propre mecanisme de revocation persiste (UserSession.revoked).
        if (tokenRevocationService.estRevoque(claims.tokenId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton révoqué");
        }
        // Ferme la fenetre residuelle documentee dans TokenRevocationService (audit securite
        // 2026-08-25) : un compte desactive par un admin (PATCH /users/{id}/status, ou en cascade via
        // Partenaire.accesActiviteCommerciale) ne doit plus pouvoir utiliser un access token deja emis,
        // meme non expire et non revoque individuellement - cf. UserActiveStatusCache.
        if (!userActiveStatusCache.isActive(claims.userId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte désactivé");
        }
        return new AuthenticatedUserPrincipal(claims.userId(), claims.username(), claims.roles());
    }

    /** Revoque l'access token courant (cf. AuthService.logout()/changePassword()) - AVANT sa propre
     * expiration naturelle, immediatement inutilisable meme si sa signature reste valide. */
    public void revoquerAccessToken(String token) {
        JwtClaims claims = parseAndValidate(token);
        if (!"access".equals(claims.type())) {
            return;
        }
        tokenRevocationService.revoquer(claims.tokenId(), claims.expiresAt());
    }

    private String generateToken(User user, String type, long ttlSeconds, Map<String, Object> extraClaims) {
        long now = Instant.now().getEpochSecond();
        long exp = now + ttlSeconds;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("username", user.getUsername());
        payload.put("roles", user.getRoles() == null ? "" : user.getRoles());
        payload.put("typ", type);
        payload.put("iat", now);
        payload.put("exp", exp);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iss", ISSUER);
        payload.putAll(extraClaims);

        String header = base64Url(GSON.toJson(Map.of("alg", "HS256", "typ", "JWT")));
        String body = base64Url(GSON.toJson(payload));
        String signature = sign(header + "." + body);
        return header + "." + body + "." + signature;
    }

    private String[] splitToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
        return parts;
    }

    /** Un jeton forge/tronque sans un claim obligatoire levait auparavant une NullPointerException
     * non interceptee (500) plutot qu'un 401 propre - audit de securite pre-production (2026-08-25). */
    private void requireClaim(Map<String, Object> payload, String name) {
        if (payload.get(name) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
    }

    private long requireLongClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (!(value instanceof Number number)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
        return number.longValue();
    }

    private Map<String, Object> decodeJson(String base64Url) {
        String json = new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de signer le jeton", e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    public record JwtClaims(Long userId, String username, String type, String tokenId, String roles, String phone,
                             Instant expiresAt, String sessionId) {
    }
}
