package ci.assurzen.controller;

import ci.assurzen.dto.AccountLockedResponse;
import ci.assurzen.dto.ErrorResponse;
import ci.assurzen.dto.IpBlockedResponse;
import ci.assurzen.dto.LoginFailureResponse;
import ci.assurzen.exception.AccountDisabledException;
import ci.assurzen.exception.AccountLockedException;
import ci.assurzen.exception.InvalidCredentialsException;
import ci.assurzen.exception.InvalidOtpCodeException;
import ci.assurzen.exception.InvalidRefreshTokenException;
import ci.assurzen.exception.IpBlockedException;
import ci.assurzen.exception.NotAuthenticatedException;
import ci.assurzen.exception.OtpExpiredException;
import ci.assurzen.exception.OtpNotFoundException;
import ci.assurzen.exception.OtpResendTooSoonException;
import ci.assurzen.exception.PasswordChangeRequiredException;
import ci.assurzen.exception.PasswordReusedException;
import ci.assurzen.exception.SmsSendException;
import ci.assurzen.exception.UnauthorizedLogoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.time.Instant;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<LoginFailureResponse> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginFailureResponse("INVALID_CREDENTIALS", exception.getMessage(), Instant.now(), exception.getRemainingAttempts()));
    }

    /**
     * Contrat "Verrouillage de compte apres echecs de connexion" : 423 (pas 401) des que le seuil
     * MAX_LOGIN_ATTEMPTS est atteint ou pendant toute la fenetre de verrouillage.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<AccountLockedResponse> accountLocked(AccountLockedException exception) {
        Instant lockedUntil = exception.getLockedUntil();
        long remainingSeconds = Math.max(0, Duration.between(Instant.now(), lockedUntil).getSeconds());
        long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new AccountLockedResponse("ACCOUNT_LOCKED",
                        "Compte temporairement bloque suite a trop de tentatives de connexion echouees. Reessayez dans " + remainingMinutes + " minute(s).",
                        Instant.now(), lockedUntil, remainingSeconds));
    }

    /** Meme contrat que accountLocked() ci-dessus, mais pour un blocage par adresse IP. */
    @ExceptionHandler(IpBlockedException.class)
    public ResponseEntity<IpBlockedResponse> ipBlocked(IpBlockedException exception) {
        Instant blockedUntil = exception.getBlockedUntil();
        long remainingSeconds = Math.max(0, Duration.between(Instant.now(), blockedUntil).getSeconds());
        long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new IpBlockedResponse("IP_BLOCKED",
                        "Adresse IP temporairement bloquee suite a trop de tentatives de connexion echouees. Reessayez dans " + remainingMinutes + " minute(s).",
                        Instant.now(), blockedUntil, remainingSeconds));
    }

    /** Mot de passe actuel incorrect au changement de mot de passe : 400 (et non 401) avec code stable - 2026-10-05. */
    @ExceptionHandler(ci.assurzen.exception.InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> invalidCurrentPassword(ci.assurzen.exception.InvalidCurrentPasswordException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ci.assurzen.exception.InvalidCurrentPasswordException.CODE, exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(PasswordReusedException.class)
    public ResponseEntity<ErrorResponse> passwordReused(PasswordReusedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("PASSWORD_REUSED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse> accountDisabled(AccountDisabledException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ACCOUNT_DISABLED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> invalidRefreshToken(InvalidRefreshTokenException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_REFRESH_TOKEN", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(UnauthorizedLogoutException.class)
    public ResponseEntity<ErrorResponse> unauthorizedLogout(UnauthorizedLogoutException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> notAuthenticated(NotAuthenticatedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ErrorResponse> passwordChangeRequired(PasswordChangeRequiredException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("PASSWORD_CHANGE_REQUIRED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(OtpNotFoundException.class)
    public ResponseEntity<ErrorResponse> otpNotFound(OtpNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("OTP_NOT_FOUND", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> otpExpired(OtpExpiredException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("OTP_EXPIRED", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(InvalidOtpCodeException.class)
    public ResponseEntity<ErrorResponse> invalidOtpCode(InvalidOtpCodeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_OTP_CODE", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ci.assurzen.exception.OtpQuotaExceededException.class)
    public ResponseEntity<ci.assurzen.dto.RetryAfterErrorResponse> otpQuota(ci.assurzen.exception.OtpQuotaExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ci.assurzen.dto.RetryAfterErrorResponse("OTP_QUOTA_EXCEEDED", exception.getMessage(), Instant.now(),
                        exception.getRetryAfterSeconds()));
    }

    @ExceptionHandler(ci.assurzen.exception.PhoneAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> phoneAlreadyRegistered(ci.assurzen.exception.PhoneAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("PHONE_ALREADY_REGISTERED", exception.getMessage(), Instant.now(), "phone"));
    }

    @ExceptionHandler(ci.assurzen.exception.InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> invalidVerificationToken(ci.assurzen.exception.InvalidVerificationTokenException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_VERIFICATION_TOKEN", exception.getMessage(), Instant.now(), "verificationToken"));
    }

    @ExceptionHandler(ci.assurzen.exception.ContractNotFoundException.class)
    public ResponseEntity<ErrorResponse> contractNotFound(ci.assurzen.exception.ContractNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CONTRACT_NOT_FOUND", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ci.assurzen.exception.ContractNotActiveException.class)
    public ResponseEntity<ErrorResponse> contractNotActive(ci.assurzen.exception.ContractNotActiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONTRACT_NOT_ACTIVE", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ci.assurzen.exception.MobilePaymentException.class)
    public ResponseEntity<?> mobilePayment(ci.assurzen.exception.MobilePaymentException exception) {
        if (exception.getRetryAfterSeconds() != null) {
            return ResponseEntity.status(exception.getHttpStatus())
                    .body(new ci.assurzen.dto.RetryAfterErrorResponse(exception.getCode(), exception.getMessage(), Instant.now(),
                            exception.getRetryAfterSeconds()));
        }
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage(), Instant.now(), exception.getField()));
    }

    @ExceptionHandler(ci.assurzen.exception.MobileApiException.class)
    public ResponseEntity<ErrorResponse> mobileApi(ci.assurzen.exception.MobileApiException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage(), Instant.now(), exception.getField()));
    }

    @ExceptionHandler(ci.assurzen.exception.PinTooWeakException.class)
    public ResponseEntity<ErrorResponse> pinTooWeak(ci.assurzen.exception.PinTooWeakException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("PIN_TOO_WEAK", exception.getMessage(), Instant.now(), "pin"));
    }

    @ExceptionHandler(ci.assurzen.exception.PinReusedException.class)
    public ResponseEntity<ErrorResponse> pinReused(ci.assurzen.exception.PinReusedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("PIN_REUSED", exception.getMessage(), Instant.now(), "newPin"));
    }

    @ExceptionHandler(OtpResendTooSoonException.class)
    public ResponseEntity<ci.assurzen.dto.OtpResendTooSoonResponse> otpResendTooSoon(OtpResendTooSoonException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ci.assurzen.dto.OtpResendTooSoonResponse("OTP_RESEND_TOO_SOON", exception.getMessage(), Instant.now(),
                        exception.getRemainingSeconds()));
    }

    /**
     * Echec de l'envoi du SMS OTP (fournisseur SMS indisponible/en erreur) : erreur infra transitoire,
     * pas une faute du client. Sans ce handler, l'exception (levee par OtpService.sendOtp) remontait
     * non geree jusqu'au 500 generique de Spring — reproduit sur /auth/login/otp-resend (bug rapporte).
     */
    @ExceptionHandler(SmsSendException.class)
    public ResponseEntity<ErrorResponse> smsSendFailed(SmsSendException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("SMS_SEND_FAILED", "Envoi du SMS impossible pour le moment, veuillez reessayer", Instant.now()));
    }
}
