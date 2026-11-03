package ci.assurzen.service;

import ci.assurzen.dto.LoginResponse;
import ci.assurzen.dto.MobileAuthDtos;
import ci.assurzen.entities.AccountDeletionRequest;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.UserSession;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.exception.InvalidVerificationTokenException;
import ci.assurzen.exception.PhoneAlreadyRegisteredException;
import ci.assurzen.exception.PinReusedException;
import ci.assurzen.exception.PinTooWeakException;
import ci.assurzen.repositories.AccountDeletionRequestRepository;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.JwtTokenService;
import ci.assurzen.security.PinPolicy;
import ci.assurzen.security.UserActiveStatusCache;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.OtpService;
import ci.assurzen.services.PasswordHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Year;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Authentification et inscription de l'application mobile (lot 1, contrat valide le 2026-11-02, cf.
 * docs/api-contracts/mobile-lot1-authentification.md). Les comptes crees ici ont le role CLIENT, le telephone
 * pour identifiant et un PIN a 6 chiffres hache comme un mot de passe. Login, refresh et logout restent ceux
 * d'AuthService.
 *
 * Pas de @Transactional sur les methodes qui appellent OtpService.verifyOtp() : ses ecritures (tentatives,
 * expiration) doivent survivre a l'exception qu'il leve (cf. la lecon documentee dans OtpService/AuthService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileAuthService {

    public static final String ROLE_CLIENT = "CLIENT";
    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String PURPOSE_RESET_PIN = "RESET_PIN";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserSessionRepository userSessionRepository;
    private final InsuredRepository insuredRepository;
    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final AccountDeletionRequestRepository accountDeletionRequestRepository;
    private final OtpService otpService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;
    private final CodeGeneratorService codeGeneratorService;
    private final UserActiveStatusCache userActiveStatusCache;
    private final AuthService authService;

    /**
     * Demande d'un code SMS. REGISTER sur un numero deja inscrit : 409 (limite par le quota). RESET_PIN sur
     * un numero inconnu (ou qui n'est pas un compte mobile) : aucun SMS, aucune erreur (pas d'enumeration).
     */
    public void demanderCode(String phone, String purpose) {
        if (PURPOSE_REGISTER.equals(purpose)) {
            if (userRepository.findByUsername(phone).isPresent()) {
                otpService.verifierQuota(phone);
                throw new PhoneAlreadyRegisteredException();
            }
        } else if (trouverClientActif(phone).isEmpty()) {
            return;
        }
        otpService.verifierQuota(phone);
        otpService.sendOtpDemande(phone, purpose);
    }

    /** Verifie le code SMS et delivre le jeton de verification du numero (usage unique, 10 min). */
    public MobileAuthDtos.OtpVerifyResponse verifierCode(String phone, String code, String purpose) {
        otpService.verifyOtp(phone, code, purpose);
        return new MobileAuthDtos.OtpVerifyResponse(jwtTokenService.generateVerificationToken(phone, purpose),
                JwtTokenService.VERIFICATION_TTL_SECONDS);
    }

    @Transactional
    public MobileAuthDtos.RegisterResponse inscrire(MobileAuthDtos.RegisterRequest request) {
        String phone = request.phone();
        if (PinPolicy.estTropFaible(request.pin())) {
            throw new PinTooWeakException();
        }
        if (userRepository.findByUsername(phone).isPresent()) {
            throw new PhoneAlreadyRegisteredException();
        }
        if (!jwtTokenService.consommerJetonVerification(request.verificationToken(), phone, PURPOSE_REGISTER)) {
            throw new InvalidVerificationTokenException();
        }

        User user = new User();
        user.setUsername(phone);
        user.setPassword(passwordEncoder.encode(request.pin()));
        user.setRoles(ROLE_CLIENT);
        user.setActive(true);
        user.setFirstConnexion(0);
        user.setCreation(Instant.now());
        user.setLastUpdate(Instant.now());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new PhoneAlreadyRegisteredException();
        }
        passwordHistoryService.record(user.getId(), user.getPassword());

        String[] noms = separerNom(request.fullName());
        Profile profile = Profile.builder()
                .code(codeGeneratorService.generate(CodePrefix.PROFILE))
                .firstname(noms[0])
                .lastname(noms[1])
                .phone(phone)
                .role(ROLE_CLIENT)
                .twoFactorEnabled(false)
                .user(user)
                .active(true)
                .expiration(false)
                .build();
        profileRepository.save(profile);

        int rattaches = rattacherAssures(user.getId(), phone);
        LoginResponse session = authService.ouvrirSession(user);
        return new MobileAuthDtos.RegisterResponse(session.user(), session.tokens(), rattaches > 0, rattaches);
    }

    /** Reinitialisation du PIN par code SMS. 204 aussi si le numero est inconnu (aucun effet). */
    public void reinitialiserPin(MobileAuthDtos.PinResetRequest request) {
        if (PinPolicy.estTropFaible(request.newPin())) {
            throw new PinTooWeakException();
        }
        Optional<User> trouve = trouverClientActif(request.phone());
        if (trouve.isEmpty()) {
            return;
        }
        User user = trouve.get();
        otpService.verifyOtp(request.phone(), request.code(), PURPOSE_RESET_PIN);
        if (passwordEncoder.matches(request.newPin(), user.getPassword())) {
            throw new PinReusedException();
        }

        passwordHistoryService.record(user.getId(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPin()));
        user.setFirstConnexion(0);
        user.setDateChangPwd(new Date());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        revoquerSessions(user.getId());
    }

    /** Verification du PIN sans session ni OTP : un echec compte dans le verrouillage (401 puis 423). */
    public void verifierPin(Long userId, String pin) {
        authService.verifierSecret(charger(userId), pin);
    }

    /** Demande de suppression de compte : PIN verifie, demande enregistree (idempotente), connexions coupees. */
    @Transactional
    public MobileAuthDtos.DeleteRequestResponse demanderSuppression(Long userId, String pin) {
        User user = charger(userId);
        authService.verifierSecret(user, pin);
        AccountDeletionRequest existante = accountDeletionRequestRepository
                .findFirstByUserIdAndStatutOrderByRequestedAtDesc(userId, "EN_ATTENTE").orElse(null);
        if (existante != null) {
            return new MobileAuthDtos.DeleteRequestResponse(existante.getReference());
        }
        AccountDeletionRequest demande = new AccountDeletionRequest();
        demande.setUserId(userId);
        demande.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        demande = accountDeletionRequestRepository.saveAndFlush(demande);
        demande.setReference(String.format("DEL-%d-%06d", Year.now().getValue(), demande.getId()));
        accountDeletionRequestRepository.save(demande);

        user.setActive(false);
        userRepository.save(user);
        userActiveStatusCache.invalidate(userId);
        revoquerSessions(userId);
        log.info("[MOBILE] demande de suppression de compte {} enregistree (utilisateur {})", demande.getReference(), userId);
        return new MobileAuthDtos.DeleteRequestResponse(demande.getReference());
    }

    // ------------------------------------------------------------------

    private Optional<User> trouverClientActif(String phone) {
        return userRepository.findByUsername(phone)
                .filter(User::isActive)
                .filter(u -> u.getRoles() != null && ROLE_CLIENT.equalsIgnoreCase(u.getRoles().trim()));
    }

    private User charger(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Compte introuvable"));
    }

    /** Lie tous les assures dont un numero correspond (rapprochement automatique valide le 2026-11-02). */
    private int rattacherAssures(Long userId, String phone) {
        List<Insured> assures = insuredRepository.findByPhoneNormalise(phone);
        int compteur = 0;
        for (Insured assure : assures) {
            if (!clientAccountInsuredRepository.existsByUserIdAndInsuredId(userId, assure.getId())) {
                clientAccountInsuredRepository.save(new ClientAccountInsured(userId, assure.getId()));
                compteur++;
            }
        }
        return compteur;
    }

    private void revoquerSessions(Long userId) {
        List<UserSession> sessions = userSessionRepository.findAllByUserIdAndRevokedFalse(userId);
        sessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(sessions);
    }

    private String[] separerNom(String nomComplet) {
        String nom = nomComplet.trim().replaceAll("\\s+", " ");
        int separateur = nom.indexOf(' ');
        if (separateur < 0) {
            return new String[]{nom, nom};
        }
        return new String[]{nom.substring(0, separateur), nom.substring(separateur + 1)};
    }
}
