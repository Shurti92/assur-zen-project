package ci.assurzen.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AuthTokens;
import ci.assurzen.dto.ChangePasswordRequest;
import ci.assurzen.dto.Credentials;
import ci.assurzen.dto.ForgotPasswordRequest;
import ci.assurzen.dto.IpLoginAttemptResponse;
import ci.assurzen.dto.LoginResponse;
import ci.assurzen.dto.OtpChallengeResponse;
import ci.assurzen.dto.ProfilePatch;
import ci.assurzen.dto.ResetPasswordWithCodeRequest;
import ci.assurzen.dto.UserResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.UserSession;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.exception.AccountDisabledException;
import ci.assurzen.exception.AccountLockedException;
import ci.assurzen.exception.InvalidCredentialsException;
import ci.assurzen.exception.InvalidOtpCodeException;
import ci.assurzen.exception.InvalidRefreshTokenException;
import ci.assurzen.exception.NotAuthenticatedException;
import ci.assurzen.exception.OtpResendTooSoonException;
import ci.assurzen.exception.PasswordChangeRequiredException;
import ci.assurzen.exception.SmsSendException;
import ci.assurzen.exception.UnauthorizedLogoutException;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.security.JwtTokenService;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.IpLoginAttemptService;
import ci.assurzen.services.LoginAttemptService;
import ci.assurzen.services.LoginHistoryService;
import ci.assurzen.services.OtpService;
import ci.assurzen.services.PasswordHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PartenaireRepository partenaireRepository;
    private final UserSessionRepository userSessionRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    // Le cycle AuthService <-> DynamicConfigurationService/FunctionalAuditRecorder est deja rompu
    // du cote de ces deux services (leur propre champ AuthService est en injection par champ
    // @Autowired @Lazy) - pas besoin de le repeter ici.
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final LoginAttemptService loginAttemptService;
    private final LoginHistoryService loginHistoryService;
    private final IpLoginAttemptService ipLoginAttemptService;
    private final PasswordHistoryService passwordHistoryService;

    /**
     * Retourne soit un {@link LoginResponse} (connexion terminee) soit un {@link OtpChallengeResponse}
     * (2FA requise : identifiants valides mais code OTP a verifier avant de delivrer les tokens).
     * La 2FA n'est declenchee que si l'utilisateur a un numero de telephone renseigne sur son profil
     * ET que la 2FA n'a pas ete explicitement desactivee sur ce profil (twoFactorEnabled) ; a defaut,
     * la connexion se termine immediatement comme avant.
     */
    @Transactional
    public Object login(Credentials credentials) {
        // Verifie AVANT meme de chercher l'utilisateur : une IP deja bloquee ne doit fuiter aucune
        // information (existence d'un compte, etc.).
        ipLoginAttemptService.assertNotBlocked();

        User user = userRepository.findByUsername(credentials.username())
                .orElseThrow(InvalidCredentialsException::new);

        enforceNotLocked(user);

        if (!matches(credentials.password(), user.getPassword())) {
            // registerFailure persiste dans sa PROPRE transaction (REQUIRES_NEW) : login() etant
            // @Transactional, l'exception levee juste apres provoquerait sinon un ROLLBACK Spring
            // qui annulerait silencieusement l'increment du compteur (voir LoginAttemptService).
            RuntimeException accountException = loginAttemptService.registerFailure(user.getId(), user.getUsername());
            recordLoginFailureSafely(user);
            // Si cet echec fait franchir a l'IP courante son PROPRE seuil, on privilegie cette
            // exception : une fois l'IP bloquee, ne plus laisser filtrer d'info sur le compte cible.
            RuntimeException ipException = ipLoginAttemptService.registerFailure();
            throw ipException != null ? ipException : accountException;
        }

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        String phone = resolvePhone(user);
        if (StringUtils.hasText(phone) && resolveTwoFactorEnabled(user)) {
            // Compteur PAS remis a zero ici : un OTP echoue derriere un mot de passe correct ne doit
            // pas rouvrir des tentatives de mot de passe "gratuites" - la connexion n'a pas encore
            // "reussi" au sens du contrat de verrouillage (registerSuccess n'est appele qu'en fin de
            // flux, ici et dans completeLoginWithOtp).
            if (otpService.delaiDeRenvoiEnCours(phone)) {
                // Un code vient d'etre envoye (delai de renvoi en cours) : on delivre quand meme un nouveau jeton de
                // defi, le code deja envoye reste valable (2026-11-02). Evite de bloquer 60 s un client qui se
                // reconnecte juste apres un premier essai ; le renvoi explicite reste /auth/login/otp-resend.
                log.info("[AUTH] connexion de {} : code OTP deja envoye, delai de renvoi en cours", user.getUsername());
            } else {
                otpService.sendOtp(phone, estCompteMobile(user));
            }
            String challengeToken = jwtTokenService.generateOtpChallengeToken(user, phone);
            return new OtpChallengeResponse(true, challengeToken, maskPhone(phone), jwtTokenService.getOtpChallengeTtlSeconds());
        }

        loginAttemptService.registerSuccess(user.getId(), user.getUsername());
        ipLoginAttemptService.registerSuccess();
        recordLoginHistorySafely(user);
        AuthTokens tokens = issueTokens(user);
        return new LoginResponse(buildUserResponse(toPrincipal(user)), tokens);
    }

    /**
     * Ouvre une session pour un compte qui vient d'etre cree (inscription mobile : la possession du numero a
     * ete prouvee par code SMS). Pas de remise a zero des compteurs d'echec : LoginAttemptService s'execute dans
     * sa propre transaction et ne verrait pas un utilisateur non encore valide ; un compte neuf n'a aucun echec.
     */
    @Transactional
    public LoginResponse ouvrirSession(User user) {
        recordLoginHistorySafely(user);
        AuthTokens tokens = issueTokens(user);
        return new LoginResponse(buildUserResponse(toPrincipal(user)), tokens);
    }

    /**
     * Verifie le secret (mot de passe / PIN) d'un utilisateur connecte SANS creer de session ni envoyer d'OTP
     * (mobile, 2026-11-02). Un echec compte dans le verrouillage de compte (401, puis 423), exactement comme
     * au login.
     */
    @Transactional
    public void verifierSecret(User user, String secret) {
        enforceNotLocked(user);
        if (!matches(secret, user.getPassword())) {
            RuntimeException accountException = loginAttemptService.registerFailure(user.getId(), user.getUsername());
            throw accountException;
        }
        loginAttemptService.registerSuccess(user.getId(), user.getUsername());
    }

    @Transactional
    public LoginResponse completeLoginWithOtp(String challengeToken, String code) {
        User user = resolveChallengeUser(challengeToken, code);

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        loginAttemptService.registerSuccess(user.getId(), user.getUsername());
        ipLoginAttemptService.registerSuccess();
        recordLoginHistorySafely(user);
        AuthTokens tokens = issueTokens(user);
        return new LoginResponse(buildUserResponse(toPrincipal(user)), tokens);
    }

    /**
     * L'historisation de la connexion (IP/device/geoloc) est un enrichissement, jamais une
     * condition de succes du login : une exception ici (base GeoIP corrompue, parsing UA
     * inattendu...) est logee et avalee, meme principe que auditService.recordAsync.
     */
    private void recordLoginHistorySafely(User user) {
        try {
            loginHistoryService.record(user);
        } catch (RuntimeException e) {
            log.warn("Echec de l'enregistrement de l'historique de connexion pour l'utilisateur {} : {}",
                    user.getUsername(), e.getMessage());
        }
    }

    /** Historique self-service (2026-10-03) : un mot de passe incorrect sur un compte existant est trace,
     * dans sa propre transaction (LoginHistoryService.recordEchec). Jamais bloquant. */
    private void recordLoginFailureSafely(User user) {
        try {
            loginHistoryService.recordEchec(user, "MOT_DE_PASSE");
        } catch (RuntimeException e) {
            log.warn("Echec de l'enregistrement de la tentative echouee pour l'utilisateur {} : {}",
                    user.getUsername(), e.getMessage());
        }
    }

    /**
     * 423 immediat, sans meme regarder le mot de passe fourni, tant que le compte est verrouille -
     * evite qu'un attaquant continue a deviner pendant le blocage et qu'une tentative reinitialise
     * silencieusement le minuteur. Un verrou expire (lockedUntil dans le passe) est simplement
     * ignore ici (pas de mutation) : failedLoginAttempts n'est remis a zero que par une connexion
     * reussie (LoginAttemptService.registerSuccess), donc un nouvel echec juste apres expiration
     * reverrouille immediatement - comportement volontaire, cf. contrat §3.3/§7.
     */
    private void enforceNotLocked(User user) {
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && Instant.now().isBefore(lockedUntil)) {
            throw new AccountLockedException(lockedUntil);
        }
    }

    /**
     * Deverrouillage manuel par un administrateur (contrat §4.2) : leve le verrou immediatement,
     * independamment du lockedUntil en cours.
     */
    @Transactional
    public void unlockAccount(String username) {
        requireAdmin();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + username));

        boolean wasLocked = user.getLockedUntil() != null || user.getFailedLoginAttempts() > 0;
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        if (wasLocked) {
            functionalAuditRecorder.recordAction("SECURITY", "Utilisateur", user.getUsername(), ActionAudit.RESTORE,
                    "Deverrouillage manuel par un administrateur");
        }
    }

    /** Deverrouillage manuel par un administrateur d'une adresse IP bloquee (contrat symetrique a unlockAccount). */
    @Transactional
    public void unblockIp(String ipAddress) {
        requireAdmin();
        ipLoginAttemptService.unblock(ipAddress);
        functionalAuditRecorder.recordAction("SECURITY", "AdresseIP", ipAddress, ActionAudit.RESTORE,
                "Deverrouillage manuel par un administrateur");
    }

    /** Blocage manuel d'une adresse IP par un administrateur (backoffice), independamment de tout echec de connexion. */
    @Transactional
    public void blockIp(String ipAddress, Integer durationMinutes) {
        requireAdmin();
        ipLoginAttemptService.block(ipAddress, durationMinutes);
        functionalAuditRecorder.recordAction("SECURITY", "AdresseIP", ipAddress, ActionAudit.SUSPEND,
                "Blocage manuel par un administrateur" + (durationMinutes != null ? " (" + durationMinutes + " minutes)" : " (indefini)"));
    }

    /** Liste des adresses IP actuellement bloquees (automatiquement ou manuellement), pour le backoffice. */
    public List<IpLoginAttemptResponse> listBlockedIps() {
        requireAdmin();
        return ipLoginAttemptService.listBlocked();
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = extractPrincipal();
        String roles = principal.roles() == null ? "" : principal.roles().toUpperCase(Locale.ROOT);
        if (!roles.contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    @Transactional
    public void resendLoginOtp(String challengeToken) {
        JwtTokenService.JwtClaims claims = requireOtpChallenge(challengeToken);
        otpService.sendOtp(claims.phone(), userRepository.findById(claims.userId()).map(this::estCompteMobile).orElse(false));
    }

    private User resolveChallengeUser(String challengeToken, String code) {
        JwtTokenService.JwtClaims claims = requireOtpChallenge(challengeToken);
        otpService.verifyOtp(claims.phone(), code);
        return loadUser(claims.userId());
    }

    private JwtTokenService.JwtClaims requireOtpChallenge(String challengeToken) {
        JwtTokenService.JwtClaims claims = jwtTokenService.parseAndValidate(challengeToken);
        if (!"otp_challenge".equals(claims.type())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton de verification invalide");
        }
        return claims;
    }

    private String resolvePhone(User user) {
        return profileRepository.findByUserId(user.getId())
                .map(Profile::getPhone)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    /** Compte de l'application mobile (role CLIENT) : ses codes SMS portent le texte AssurZen Mobile. */
    private boolean estCompteMobile(User user) {
        return user.getRoles() != null && "CLIENT".equalsIgnoreCase(user.getRoles().trim());
    }

    private boolean resolveTwoFactorEnabled(User user) {
        return profileRepository.findByUserId(user.getId())
                .map(Profile::isTwoFactorEnabled)
                .orElse(true);
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 4) {
            return "****";
        }
        int visible = 2;
        String start = phone.substring(0, visible);
        String end = phone.substring(phone.length() - visible);
        return start + "*".repeat(phone.length() - 2 * visible) + end;
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        JwtTokenService.JwtClaims claims = jwtTokenService.parseAndValidate(refreshToken);
        if (!"refresh".equals(claims.type())) {
            throw new InvalidRefreshTokenException();
        }

        // Verrou sur la ligne du jeton (2026-10-04) : un second renouvellement simultane attend la fin du
        // premier, puis voit le jeton deja consomme. Avant, les deux le lisaient "actif" et obtenaient
        // chacun de nouveaux jetons (jeton reutilisable deux fois s'il etait rejoue en meme temps).
        UserSession currentSession = userSessionRepository.findByTokenForUpdate(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!currentSession.getUserId().equals(claims.userId())) {
            throw new InvalidRefreshTokenException();
        }

        if (currentSession.isRevoked()) {
            if (!renouvellementConcurrentTolere(currentSession)) {
                throw new InvalidRefreshTokenException();
            }
            log.info("Renouvellement concurrent tolere pour la session {} (jeton consomme il y a moins de {} s)",
                    currentSession.getSessionId(), DELAI_RENOUVELLEMENT_CONCURRENT.toSeconds());
        } else {
            currentSession.setRevoked(true);
            currentSession.setRotatedAt(Instant.now());
            userSessionRepository.save(currentSession);
        }

        User user = userRepository.findById(claims.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        return issueTokens(user, currentSession);
    }

    /** Fenetre pendant laquelle un jeton qui vient d'etre consomme par un renouvellement est encore accepte. */
    static final java.time.Duration DELAI_RENOUVELLEMENT_CONCURRENT = java.time.Duration.ofSeconds(10);

    /**
     * Un refresh token deja consomme reste accepte si (et seulement si) : il a ete consomme par une
     * ROTATION (pas par une deconnexion), il y a moins de DELAI_RENOUVELLEMENT_CONCURRENT, et sa session a
     * encore un jeton actif (pas de deconnexion ni de changement de mot de passe depuis). Couvre le cas
     * legitime de l'application qui renouvelle deux fois en meme temps (rechargement, onglets) sans
     * deconnecter l'utilisateur ; au-dela, ou pour un jeton revoque, 401 comme avant.
     */
    boolean renouvellementConcurrentTolere(UserSession session) {
        return session.getRotatedAt() != null
                && session.getRotatedAt().isAfter(Instant.now().minus(DELAI_RENOUVELLEMENT_CONCURRENT))
                && StringUtils.hasText(session.getSessionId())
                && userSessionRepository.existsBySessionIdAndRevokedFalse(session.getSessionId());
    }

    /**
     * rawAccessToken (facultatif) : le jeton brut porte par la requete courante - permet de revoquer
     * IMMEDIATEMENT cet access token (audit de securite pre-production, 2026-08-23), en plus de la
     * revocation deja existante du refresh token (UserSession.revoked). Null tolere (n'annule pas le
     * logout) pour ne pas casser un appelant qui ne le fournirait pas.
     */
    @Transactional
    public void logout(String rawAccessToken) {
        AuthenticatedUserPrincipal principal = extractPrincipal();
        List<UserSession> sessions = userSessionRepository.findAllByUserIdAndRevokedFalse(principal.userId());
        if (sessions.isEmpty()) {
            throw new UnauthorizedLogoutException();
        }

        sessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(sessions);
        revoquerAccessTokenCourantSiPresent(rawAccessToken);
    }

    /** Cf. logout(String) pour le role de rawAccessToken. */
    @Transactional
    public void changePassword(ChangePasswordRequest request, String rawAccessToken) {
        AuthenticatedUserPrincipal principal = extractPrincipal();
        User user = loadUser(principal.userId());

        if (!matches(request.currentPassword(), user.getPassword())) {
            throw new ci.assurzen.exception.InvalidCurrentPasswordException();
        }

        passwordHistoryService.assertNotReused(user, request.newPassword());
        passwordHistoryService.record(user.getId(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFirstConnexion(0);
        user.setDateChangPwd(new Date());
        userRepository.save(user);

        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedFalse(user.getId());
        activeSessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(activeSessions);
        revoquerAccessTokenCourantSiPresent(rawAccessToken);
    }

    private void revoquerAccessTokenCourantSiPresent(String rawAccessToken) {
        if (!StringUtils.hasText(rawAccessToken)) {
            return;
        }
        try {
            jwtTokenService.revoquerAccessToken(rawAccessToken);
        } catch (RuntimeException e) {
            log.warn("Echec de la revocation immediate de l'access token courant (non bloquant)", e);
        }
    }

    /**
     * Toujours silencieux (aucune exception, quel que soit le username) pour ne jamais reveler
     * si un compte existe. Le OTP_RESEND_TOO_SOON du service OTP est egalement avale pour la
     * meme raison : sinon un compte existant + un compte inexistant deviendraient distinguables
     * en renvoyant la requete rapidement deux fois de suite. SmsSendException (fournisseur SMS en
     * echec) est avalee pour la meme raison : la laisser remonter (meme geree en 503 par
     * AuthExceptionHandler) reviendrait a confirmer qu'un SMS a ete tente, donc qu'un compte+numero
     * existent.
     */
    // Pas de @Transactional englobant (correctif du 2026-10-05) : otpService.sendOtp() a sa propre transaction.
    // Englobee ici, l'OtpResendTooSoonException qu'elle leve (demande repetee avant le delai de renvoi) marquait
    // la transaction "rollback-only" : l'exception etait bien avalee ci-dessous, mais le commit final echouait
    // en UnexpectedRollbackException -> 500, ce qui revelait aussi l'existence du compte (200 sinon).
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByUsername(request.username())
                .filter(User::isActive)
                .ifPresent(user -> {
                    String phone = resolvePhone(user);
                    if (StringUtils.hasText(phone)) {
                        try {
                            otpService.sendOtp(phone, estCompteMobile(user));
                        } catch (OtpResendTooSoonException | SmsSendException ignored) {
                            // un code est deja en cours d'envoi, ou le SMS a echoue : rien a faire (voir javadoc)
                        }
                    }
                });
    }

    /**
     * Pas de @Transactional englobant ici : otpService.verifyOtp() n'est lui-meme pas
     * transactionnel precisement pour que ses ecritures (compteur de tentatives, expiration)
     * survivent a l'exception qu'il leve en cas d'echec (meme lecon que dans OtpService).
     * Englober cet appel dans une transaction ici annulerait ces ecritures au rollback.
     */
    public void resetPasswordWithCode(ResetPasswordWithCodeRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidOtpCodeException("Code invalide"));

        String phone = resolvePhone(user);
        if (!StringUtils.hasText(phone)) {
            throw new InvalidOtpCodeException("Code invalide");
        }

        otpService.verifyOtp(phone, request.code());

        passwordHistoryService.assertNotReused(user, request.newPassword());
        passwordHistoryService.record(user.getId(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFirstConnexion(0);
        user.setDateChangPwd(new Date());
        userRepository.save(user);

        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedFalse(user.getId());
        activeSessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(activeSessions);
    }

    public UserResponse getMe() {
        return buildUserResponse(extractPrincipal());
    }

    @Transactional
    public UserResponse updateMe(ProfilePatch patch) {
        AuthenticatedUserPrincipal principal = currentPrincipal();
        Profile profile = profileRepository.findByUserId(principal.userId())
                .orElseGet(() -> Profile.builder()
                        .code(codeGeneratorService.generate(CodePrefix.PROFILE))
                        .user(loadUser(principal.userId()))
                        .firstname(principal.username())
                        .lastname(principal.username())
                        .active(true)
                        .expiration(false)
                        .build());

        if (patch.fullName() != null && !patch.fullName().isBlank()) {
            String[] parts = splitFullName(patch.fullName());
            profile.setFirstname(parts[0]);
            profile.setLastname(parts[1]);
        }
        if (patch.email() != null) {
            profile.setEmail(patch.email());
        }
        if (patch.sector() != null) {
            profile.setSector(patch.sector());
        }
        if (patch.langueCode() != null) {
            profile.setLangueCode(patch.langueCode());
        }
        if (patch.bio() != null) {
            profile.setBio(patch.bio());
        }
        if (patch.facebookUrl() != null) {
            profile.setFacebookUrl(patch.facebookUrl());
        }
        if (patch.twitterUrl() != null) {
            profile.setTwitterUrl(patch.twitterUrl());
        }
        if (patch.linkedinUrl() != null) {
            profile.setLinkedinUrl(patch.linkedinUrl());
        }
        if (patch.instagramUrl() != null) {
            profile.setInstagramUrl(patch.instagramUrl());
        }
        if (patch.country() != null) {
            profile.setCountry(patch.country());
        }
        if (patch.city() != null) {
            profile.setCity(patch.city());
        }
        if (patch.state() != null) {
            profile.setState(patch.state());
        }
        if (patch.postalCode() != null) {
            profile.setPostalCode(patch.postalCode());
        }
        if (patch.taxId() != null) {
            profile.setTaxId(patch.taxId());
        }
        if (patch.twoFactorEnabled() != null) {
            profile.setTwoFactorEnabled(patch.twoFactorEnabled());
        }

        profileRepository.save(profile);
        return buildUserResponse(principal);
    }

    /**
     * Principal courant, avec verification que le mot de passe n'est pas en attente de changement.
     * A utiliser par defaut (scoping des services metier). Pour les actions d'auto-gestion du compte
     * qui doivent rester accessibles meme quand un changement de mot de passe est requis
     * (changePassword, logout, getMe), utiliser {@link #extractPrincipal()} directement.
     */
    public AuthenticatedUserPrincipal currentPrincipal() {
        AuthenticatedUserPrincipal principal = extractPrincipal();
        if (loadUser(principal.userId()).getFirstConnexion() == 1) {
            throw new PasswordChangeRequiredException();
        }
        return principal;
    }

    private AuthenticatedUserPrincipal extractPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            throw new NotAuthenticatedException();
        }
        return principal;
    }

    private AuthTokens issueTokens(User user) {
        return issueTokens(user, null);
    }

    /**
     * precedente : la ligne du refresh token qui vient d'etre consomme (renouvellement), null pour une
     * nouvelle connexion. Appareils connectes (2026-10-03) : la session garde le meme sessionId et la
     * meme date de connexion d'un renouvellement a l'autre ; appareil, IP et derniere activite sont
     * mis a jour a chaque renouvellement. L'access token porte le sessionId (claim "sid").
     */
    private AuthTokens issueTokens(User user, UserSession precedente) {
        long ttlSeconds = dynamicConfigurationService.getLong("SESSION_TIMEOUT", jwtTokenService.getAccessTtlSeconds());
        String sessionId = precedente != null && StringUtils.hasText(precedente.getSessionId())
                ? precedente.getSessionId() : java.util.UUID.randomUUID().toString();
        String accessToken = jwtTokenService.generateAccessToken(user, ttlSeconds, sessionId);
        String refreshToken = jwtTokenService.generateRefreshToken(user);
        JwtTokenService.JwtClaims accessClaims = jwtTokenService.parseAndValidate(accessToken);
        LoginHistoryService.ClientInfo client = clientCourantSafely();
        Instant maintenant = Instant.now();
        Instant connexion = precedente == null ? maintenant
                : precedente.getConnectedAt() != null ? precedente.getConnectedAt() : precedente.getCreatedAt();

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .token(refreshToken)
                .revoked(false)
                .sessionId(sessionId)
                .ipAddress(client == null ? null : client.ip())
                .deviceType(client == null ? null : client.deviceType())
                .browser(client == null ? null : client.browser())
                .os(client == null ? null : client.os())
                .country(client == null ? null : client.country())
                .city(client == null ? null : client.city())
                .connectedAt(connexion)
                .lastActivityAt(maintenant)
                .accessJti(accessClaims.tokenId())
                .accessExpiresAt(accessClaims.expiresAt())
                .build();
        userSessionRepository.save(session);

        return new AuthTokens(accessToken, refreshToken, "Bearer", ttlSeconds);
    }

    /** Appareil/localisation de la requete : enrichissement seulement, jamais une condition de succes. */
    private LoginHistoryService.ClientInfo clientCourantSafely() {
        try {
            return loginHistoryService.clientCourant();
        } catch (RuntimeException e) {
            log.warn("Appareil de la session non determine : {}", e.getMessage());
            return null;
        }
    }

    private UserResponse buildUserResponse(AuthenticatedUserPrincipal principal) {
        Profile profile = profileRepository.findByUserId(principal.userId()).orElse(null);
        String fullName = profile != null && StringUtils.hasText(profile.getFirstname())
                ? profile.getFirstname() + " " + safeString(profile.getLastname())
                : principal.username();
        String email = profile != null && StringUtils.hasText(profile.getEmail()) ? profile.getEmail() : principal.username();
        String role = profile != null && StringUtils.hasText(profile.getRole()) ? profile.getRole() : safeString(principal.roles());
        String sector = profile != null && StringUtils.hasText(profile.getSector()) ? profile.getSector() : "";
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;
        User user = loadUser(principal.userId());
        boolean mustChangePassword = user.getFirstConnexion() == 1;
        Long typeProfileId = user.getTypeProfile() == null ? null : user.getTypeProfile().getId();
        boolean twoFactorEnabled = profile == null || profile.isTwoFactorEnabled();
        // Non-null uniquement si ce profil est celui d'un partenaire (compte cree automatiquement via
        // Partenaire.typeProfile.declencheCreationCompte, cf. PartenaireService) - pas de comparaison
        // en dur sur le libelle "REPRESENTANT", on se base sur la relation reelle Partenaire.profile.
        // Plusieurs Partenaire lies au meme Profile = donnee anormale (rien ne l'empeche en base) :
        // on ne devine pas lequel afficher, on journalise et on renvoie null plutot que d'exposer un
        // partenaireId potentiellement faux au frontend.
        Long partenaireId = null;
        if (profile != null) {
            List<Partenaire> partenaires = partenaireRepository.findByProfile_Id(profile.getId());
            if (partenaires.size() == 1) {
                partenaireId = partenaires.get(0).getId();
            } else if (partenaires.size() > 1) {
                log.warn("Profile {} est lie a {} partenaires ({}), impossible de determiner un partenaireId unique",
                        profile.getId(), partenaires.size(),
                        partenaires.stream().map(Partenaire::getId).toList());
            }
        }

        return new UserResponse(
                String.valueOf(principal.userId()), fullName, email, role, sector, avatarUrl, mustChangePassword,
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getFacebookUrl(),
                profile == null ? null : profile.getTwitterUrl(),
                profile == null ? null : profile.getLinkedinUrl(),
                profile == null ? null : profile.getInstagramUrl(),
                profile == null ? null : profile.getCountry(),
                profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getState(),
                profile == null ? null : profile.getPostalCode(),
                profile == null ? null : profile.getTaxId(),
                twoFactorEnabled,
                profile == null ? null : profile.getLangueCode(),
                partenaireId,
                typeProfileId,
                profile == null ? null : profile.getPhone());
    }

    private AuthenticatedUserPrincipal toPrincipal(User user) {
        return new AuthenticatedUserPrincipal(user.getId(), user.getUsername(), user.getRoles());
    }

    private boolean matches(String rawPassword, String storedPassword) {
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    private String[] splitFullName(String fullName) {
        String trimmed = fullName.trim();
        int separator = trimmed.indexOf(' ');
        if (separator < 0) {
            return new String[]{trimmed, trimmed};
        }

        String firstname = trimmed.substring(0, separator).trim();
        String lastname = trimmed.substring(separator + 1).trim();
        if (lastname.isBlank()) {
            lastname = firstname;
        }
        return new String[]{firstname, lastname};
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable: " + userId));
    }
}
