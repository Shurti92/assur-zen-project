package ci.assurzen.services;

import ci.assurzen.entities.OtpCode;
import ci.assurzen.exception.InvalidOtpCodeException;
import ci.assurzen.exception.OtpExpiredException;
import ci.assurzen.exception.OtpNotFoundException;
import ci.assurzen.exception.OtpResendTooSoonException;
import ci.assurzen.exception.SmsSendException;
import ci.assurzen.repositories.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    // Valeurs de repli si les cles ci-dessous sont absentes/inactives en base (Dynamic
    // Configuration Engine) - le comportement par defaut reste identique a avant leur ajout.
    private static final long DEFAULT_CODE_VALIDITY_MINUTES = 5;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_RESEND_COOLDOWN_SECONDS = 60;
    private static final int DEFAULT_CODE_LENGTH = 6;

    private final OtpCodeRepository otpCodeRepository;
    private final SmsNotificationService smsNotificationService;
    private final PasswordEncoder passwordEncoder;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Quota de demandes de code par numero et par heure (mobile, 2026-11-02) : OTP_MAX_REQUESTS_PER_HOUR
     * (defaut 3). A appeler AVANT sendOtp() (appels separes : sendOtp a sa propre transaction). Les envois
     * SMS echoues ne comptent pas (leur code est annule).
     */
    public void verifierQuota(String phone) {
        int max = dynamicConfigurationService.getInteger("OTP_MAX_REQUESTS_PER_HOUR", 3);
        Instant depuis = Instant.now().minus(Duration.ofHours(1));
        java.util.List<OtpCode> recents = otpCodeRepository.findByPhoneAndMobileRequestTrueAndCreatedAtAfterOrderByCreatedAtAsc(phone, depuis);
        if (recents.size() >= max) {
            Instant debloque = recents.get(recents.size() - max).getCreatedAt().plus(Duration.ofHours(1));
            long attente = Math.max(1, Duration.between(Instant.now(), debloque).getSeconds() + 1);
            throw new ci.assurzen.exception.OtpQuotaExceededException(attente);
        }
    }

    /** Vrai si un code vient d'etre envoye et que le delai de renvoi court encore (sans lever d'exception ni marquer de transaction). */
    public boolean delaiDeRenvoiEnCours(String phone) {
        try {
            checkResendCooldown(phone);
            return false;
        } catch (ci.assurzen.exception.OtpResendTooSoonException e) {
            return true;
        }
    }

    @Transactional
    public void sendOtp(String phone) {
        envoyerCode(phone, false);
    }

    /** Envoi d'un code avec, pour l'application mobile (comptes CLIENT, /auth/mobile/*), le texte AssurZen Mobile (2026-11-02). */
    @Transactional
    public void sendOtp(String phone, boolean mobile) {
        envoyerCode(phone, mobile);
    }

    /** Demande de code depuis /auth/mobile/otp/request : texte mobile et comptee dans le quota horaire. */
    @Transactional
    public void sendOtpDemande(String phone, String purpose) {
        envoyerCode(phone, true, purpose);
    }

    private void envoyerCode(String phone, boolean mobile) {
        envoyerCode(phone, mobile, null);
    }

    private void envoyerCode(String phone, boolean mobile, String purpose) {
        checkResendCooldown(phone);
        invalidatePendingCodes(phone);

        long validityMinutes = dynamicConfigurationService.getLong("OTP_CODE_VALIDITY_MINUTES", DEFAULT_CODE_VALIDITY_MINUTES);
        String code = generateCode();
        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(validityMinutes)))
                .consumed(false)
                .attempts(0)
                .mobileRequest(purpose != null ? Boolean.TRUE : null)
                .purpose(purpose)
                .build();
        otpCodeRepository.save(otpCode);

        boolean sent = smsNotificationService.sendOtpSms(phone, code, validityMinutes, mobile);
        if (!sent) {
            // @Transactional: throwing here rolls back the OtpCode save above, so a failed send
            // never leaves a dangling code the user can't have received, and the resend cooldown
            // check (based on row creation time) won't block an immediate retry.
            throw new SmsSendException("Echec de l'envoi du SMS OTP", null);
        }
    }

    /**
     * Pas de @Transactional ici : chaque etat (tentative echouee, expiration, consommation)
     * doit etre persiste independamment meme si la methode se termine par une exception
     * (une transaction englobante annulerait ces ecritures au rollback).
     */
    public void verifyOtp(String phone, String code) {
        verifyOtp(phone, code, null);
    }

    /**
     * Verifie le code en attente, qui doit avoir ete emis pour cet objet (null = connexion / portail ; REGISTER ou
     * RESET_PIN = application mobile). Un code d'un autre objet est refuse sans etre consomme (2026-11-02).
     */
    public void verifyOtp(String phone, String code, String purpose) {
        OtpCode otpCode = otpCodeRepository.findTopByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(OtpNotFoundException::new);

        if (!java.util.Objects.equals(purpose, otpCode.getPurpose())) {
            throw new InvalidOtpCodeException("Code invalide");
        }

        if (Instant.now().isAfter(otpCode.getExpiresAt())) {
            otpCode.setConsumed(true);
            otpCodeRepository.save(otpCode);
            throw new OtpExpiredException();
        }

        if (!passwordEncoder.matches(code, otpCode.getCodeHash())) {
            otpCode.setAttempts(otpCode.getAttempts() + 1);
            int maxAttempts = dynamicConfigurationService.getInteger("OTP_MAX_ATTEMPTS", DEFAULT_MAX_ATTEMPTS);
            if (otpCode.getAttempts() >= maxAttempts) {
                otpCode.setConsumed(true);
                otpCodeRepository.save(otpCode);
                throw new InvalidOtpCodeException("Nombre maximal de tentatives atteint, veuillez redemander un code");
            }
            otpCodeRepository.save(otpCode);
            throw new InvalidOtpCodeException("Code invalide");
        }

        otpCode.setConsumed(true);
        otpCodeRepository.save(otpCode);
    }

    private void checkResendCooldown(String phone) {
        Duration cooldown = Duration.ofSeconds(dynamicConfigurationService.getLong("OTP_RESEND_COOLDOWN_SECONDS", DEFAULT_RESEND_COOLDOWN_SECONDS));
        otpCodeRepository.findTopByPhoneOrderByCreatedAtDesc(phone).ifPresent(lastCode -> {
            // Dernier code deja UTILISE avec succes (consomme sans avoir epuise ses tentatives) : pas de delai
            // (2026-10-05). Ex. : connexion juste apres une reinitialisation de mot de passe, qui etait bloquee
            // en 429 par le delai du code de reinitialisation. Un code en attente, ou epuise par trop
            // d'essais, garde le delai anti-abus.
            int maxAttempts = dynamicConfigurationService.getInteger("OTP_MAX_ATTEMPTS", DEFAULT_MAX_ATTEMPTS);
            if (lastCode.isConsumed() && lastCode.getAttempts() < maxAttempts) {
                return;
            }
            Instant cooldownEnd = lastCode.getCreatedAt().plus(cooldown);
            if (Instant.now().isBefore(cooldownEnd)) {
                long remainingSeconds = Duration.between(Instant.now(), cooldownEnd).getSeconds() + 1;
                throw new OtpResendTooSoonException(remainingSeconds);
            }
        });
    }

    private void invalidatePendingCodes(String phone) {
        List<OtpCode> pending = otpCodeRepository.findAllByPhoneAndConsumedFalse(phone);
        pending.forEach(otpCode -> otpCode.setConsumed(true));
        otpCodeRepository.saveAll(pending);
    }

    private String generateCode() {
        int length = dynamicConfigurationService.getInteger("OTP_CODE_LENGTH", DEFAULT_CODE_LENGTH);
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", secureRandom.nextInt(bound));
    }
}
