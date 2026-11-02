package ci.assurzen.controller;

import ci.assurzen.dto.AuthTokens;
import ci.assurzen.dto.BlockIpRequest;
import ci.assurzen.dto.ChangePasswordRequest;
import ci.assurzen.dto.Credentials;
import ci.assurzen.dto.ForgotPasswordRequest;
import ci.assurzen.dto.IpLoginAttemptResponse;
import ci.assurzen.dto.LoginOtpResendRequest;
import ci.assurzen.dto.LoginOtpVerifyRequest;
import ci.assurzen.dto.LoginResponse;
import ci.assurzen.dto.ProfilePatch;
import ci.assurzen.dto.RefreshRequest;
import ci.assurzen.dto.ResetPasswordWithCodeRequest;
import ci.assurzen.dto.SendOtpRequest;
import ci.assurzen.dto.UserResponse;
import ci.assurzen.dto.VerifyOtpRequest;
import ci.assurzen.service.AuthService;
import ci.assurzen.services.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("${env.ws}auth")
@Tag(name = "Login", description = "Authentification & profil")
@RequiredArgsConstructor
public class LoginController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    /** Ancien Path du cookie (avant le correctif du 25/08/2026, cf. poserCookieRefreshToken) - un
     * navigateur deja utilise avant ce correctif peut porter un cookie refresh_token sur ce Path,
     * distinct du nouveau Path("/"). Comme les deux coexistent sous le meme nom, le navigateur envoie
     * les DEUX au serveur (le plus specifique, celui-ci, en premier) - extraireRefreshTokenCookie() ne
     * lit que le premier trouve, donc l'ancien (potentiellement deja revoque) masque systematiquement
     * le nouveau tant qu'il n'a pas ete efface explicitement. Confirme en audit le 25/08/2026 : un
     * /auth/refresh valide echouait en 401 a cause de cet ancien cookie. A retirer une fois raisonnable
     * de considerer qu'aucun navigateur actif ne porte plus l'ancien Path (au plus tard 7 jours apres
     * deploiement, duree de vie max d'un refresh token). */
    private static final String LEGACY_COOKIE_PATH = "/api-assur-zen/auth";

    private final AuthService authService;
    private final OtpService otpService;

    /** Meme propriete que celle utilisee par JwtTokenService pour generer le refresh token lui-meme -
     * le cookie doit expirer au plus tard en meme temps que le jeton qu'il transporte. */
    @Value("${app.jwt.refresh-ttl-seconds:604800}")
    private long refreshTtlSeconds;

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Authentifier un commercial (renvoie soit les tokens, soit un challenge OTP si la 2FA est activee pour ce compte)")
    @SecurityRequirements
    public ResponseEntity<Object> login(@Valid @RequestBody Credentials credentials, HttpServletResponse httpResponse) {
        Object result = authService.login(credentials);
        if (result instanceof LoginResponse loginResponse) {
            poserCookieRefreshToken(httpResponse, loginResponse.tokens());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * refreshToken lu depuis le cookie httpOnly "refresh_token" s'il est present (client same-origin,
     * migration du 2026-08-23 - cf. Proposition technique cookie httpOnly), sinon depuis le corps de
     * la requete comme avant (client cross-origin, ex. le frontend "vente" sur Vercel, pour lequel le
     * cookie ne peut de toute facon jamais etre envoye par le navigateur - domaine different). Les deux
     * mecanismes cohabitent indefiniment, aucun n'est retire.
     */
    @PostMapping("/refresh")
    @Operation(operationId = "refreshTokens", summary = "Rafraîchir les tokens (refresh silencieux sur 401). "
            + "Le refresh token peut être fourni via le cookie httpOnly 'refresh_token' (client same-origin) ou dans le corps de la requête (client cross-origin) - l'un des deux est requis.")
    @SecurityRequirements
    public ResponseEntity<AuthTokens> refreshTokens(@RequestBody(required = false) RefreshRequest request,
                                                      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = extraireRefreshTokenCookie(httpRequest);
        if (!StringUtils.hasText(refreshToken) && request != null) {
            refreshToken = request.refreshToken();
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Refresh token requis (cookie 'refresh_token' ou corps de la requete)");
        }
        AuthTokens tokens = authService.refresh(refreshToken);
        poserCookieRefreshToken(httpResponse, tokens);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "Déconnexion (invalide le refresh token et l'access token courant côté serveur)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(extraireAccessToken(httpRequest));
        effacerCookieRefreshToken(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get-me-data")
    @Operation(operationId = "getMe", summary = "Profil de l'utilisateur connecté")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }

    /**
     * Alias GET de /get-me-data sur le chemin REST conventionnel /me (a cote de PATCH /me deja
     * existant pour updateMe) - ajoute car le frontend "Force de Vente" appelle GET /auth/me pour
     * restaurer la session au chargement de page, recevait un 405 (seul PATCH /me etait mappe), et se
     * deconnectait donc a chaque F5 malgre un token valide. Ne remplace pas /get-me-data, deja utilise
     * par assur-zen-core.
     */
    @GetMapping("/me")
    @Operation(operationId = "getMeAlias", summary = "Profil de l'utilisateur connecté (alias GET /me)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserResponse> getMeAlias() {
        return ResponseEntity.ok(authService.getMe());
    }

    @PatchMapping("/me")
    @Operation(operationId = "updateMe", summary = "Mettre à jour son profil")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody ProfilePatch patch) {
        return ResponseEntity.ok(authService.updateMe(patch));
    }

    @PatchMapping("/change-password")
    @Operation(operationId = "changePassword", summary = "Changer son propre mot de passe")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(request, extraireAccessToken(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    @Operation(operationId = "verifyOtp", summary = "Verifier un code de verification (OTP) soumis")
    @SecurityRequirements
    public ResponseEntity<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtp(request.phone(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/resend")
    @Operation(operationId = "resendOtp", summary = "Redemander l'envoi d'un code de verification (OTP)")
    @SecurityRequirements
    public ResponseEntity<Void> resendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.sendOtp(request.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login/otp-verify")
    @Operation(operationId = "verifyLoginOtp", summary = "Terminer la connexion (2FA) en verifiant le code OTP lie au challenge de login")
    @SecurityRequirements
    public ResponseEntity<LoginResponse> verifyLoginOtp(@Valid @RequestBody LoginOtpVerifyRequest request, HttpServletResponse httpResponse) {
        LoginResponse response = authService.completeLoginWithOtp(request.challengeToken(), request.code());
        poserCookieRefreshToken(httpResponse, response.tokens());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/otp-resend")
    @Operation(operationId = "resendLoginOtp", summary = "Redemander le code OTP pour un challenge de login (2FA) en cours")
    @SecurityRequirements
    public ResponseEntity<Void> resendLoginOtp(@Valid @RequestBody LoginOtpResendRequest request) {
        authService.resendLoginOtp(request.challengeToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(operationId = "forgotPassword", summary = "Demander un code de reinitialisation de mot de passe par SMS")
    @SecurityRequirements
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(operationId = "resetPasswordWithCode", summary = "Reinitialiser son mot de passe avec le code recu par SMS")
    @SecurityRequirements
    public ResponseEntity<Void> resetPasswordWithCode(@Valid @RequestBody ResetPasswordWithCodeRequest request) {
        authService.resetPasswordWithCode(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{username}/unlock")
    @Operation(operationId = "unlockAccount", summary = "Deverrouiller manuellement un compte bloque (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unlockAccount(@PathVariable String username) {
        authService.unlockAccount(username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ips/blocked")
    @Operation(operationId = "listBlockedIps", summary = "Lister les adresses IP actuellement bloquees, automatiquement ou manuellement (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<IpLoginAttemptResponse>> listBlockedIps() {
        return ResponseEntity.ok(authService.listBlockedIps());
    }

    @PostMapping("/ips/{ip}/block")
    @Operation(operationId = "blockIp", summary = "Bloquer manuellement une adresse IP depuis le backoffice (admin) ; sans duree fournie, blocage indefini jusqu'a deblocage manuel")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> blockIp(@PathVariable String ip, @RequestBody(required = false) BlockIpRequest request) {
        authService.blockIp(ip, request != null ? request.durationMinutes() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ips/{ip}/unblock")
    @Operation(operationId = "unblockIp", summary = "Deverrouiller manuellement une adresse IP bloquee (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unblockIp(@PathVariable String ip) {
        authService.unblockIp(ip);
        return ResponseEntity.noContent().build();
    }

    private String extraireAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * Migration cookie httpOnly (2026-08-23, proposition technique equipe frontend). SameSite "None"
     * (2026-08-23, ajuste le meme jour - initialement "Strict") : le frontend "Espace Partenaire"
     * (assurzen.ci) est sur un domaine distinct de la gateway (medicis-apps.com), donc cross-site du
     * point de vue du navigateur - "Strict"/"Lax" auraient empeche l'envoi du cookie sur ces appels.
     * "None" exige "Secure" (deja actif ici, HTTPS partout).
     * Path("/") (audit de securite pre-production, 2026-08-25) : initialement wsPrefix + "auth" (ex.
     * "/api-assur-zen/auth"), sur l'hypothese que le prefixe vu par le navigateur correspond au prefixe
     * interne Spring. Faux en RECETTE/PROD - ces environnements sont servis derriere un reverse-proxy
     * qui ajoute "/assur-zen-gateway(-recette)/" AVANT "/api-assur-zen/" (cf. environment.recette.ts /
     * environment.prod.ts cote frontend), prefixe que cette appli elle-meme ignore completement. Le
     * Path du cookie ne prefixait donc jamais le chemin reel des requetes en RECETTE/PROD -> le cookie
     * n'aurait jamais ete renvoye par le navigateur sur /auth/refresh dans ces environnements. "/" est
     * la seule valeur correcte quel que soit le prefixe de reverse-proxy en amont (httpOnly : rester
     * large ici n'expose rien de plus a du JS, qui ne peut de toute facon jamais le lire).
     */
    private void poserCookieRefreshToken(HttpServletResponse httpResponse, AuthTokens tokens) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, tokens.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(java.time.Duration.ofSeconds(refreshTtlSeconds))
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, effacerAncienCookieHeader());
    }

    private void effacerCookieRefreshToken(HttpServletResponse httpResponse) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, effacerAncienCookieHeader());
    }

    /** Cf. LEGACY_COOKIE_PATH : un second Set-Cookie, sur l'ancien Path, pour effacer un cookie deja
     * pose par un navigateur utilise avant le correctif du 25/08/2026 - plutot que d'attendre son
     * expiration naturelle (jusqu'a 7 jours) pendant laquelle il masquerait le nouveau cookie. */
    private String effacerAncienCookieHeader() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(LEGACY_COOKIE_PATH)
                .maxAge(0)
                .build()
                .toString();
    }

    private String extraireRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
