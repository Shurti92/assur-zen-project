package ci.assurzen.services;

import ci.assurzen.entities.OtpCode;
import ci.assurzen.exception.OtpResendTooSoonException;
import ci.assurzen.repositories.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Delai de renvoi de l'OTP (2026-10-05) : pas de delai apres un code utilise avec succes. */
class OtpServiceCooldownTest {

    private static final String TEL = "0000000104";

    private final OtpCodeRepository repository = mock(OtpCodeRepository.class);
    private final SmsNotificationService sms = mock(SmsNotificationService.class);
    private final DynamicConfigurationService configuration = mock(DynamicConfigurationService.class);
    private final OtpService service = new OtpService(repository, sms, mock(PasswordEncoder.class), configuration);

    @BeforeEach
    void setUp() {
        when(configuration.getLong(eq("OTP_RESEND_COOLDOWN_SECONDS"), anyLong())).thenReturn(60L);
        when(configuration.getLong(eq("OTP_CODE_VALIDITY_MINUTES"), anyLong())).thenReturn(5L);
        when(configuration.getInteger(eq("OTP_MAX_ATTEMPTS"), anyInt())).thenReturn(5);
        when(configuration.getInteger(eq("OTP_CODE_LENGTH"), anyInt())).thenReturn(6);
        when(repository.findAllByPhoneAndConsumedFalse(TEL)).thenReturn(List.of());
        when(sms.sendOtpSms(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    private void dernierCode(boolean consomme, int tentatives) {
        when(repository.findTopByPhoneOrderByCreatedAtDesc(TEL)).thenReturn(Optional.of(OtpCode.builder()
                .phone(TEL).consumed(consomme).attempts(tentatives).createdAt(Instant.now().minusSeconds(10)).build()));
    }

    @Test
    void codeEnAttenteRecent_429AvecSecondesRestantes() {
        dernierCode(false, 0);

        OtpResendTooSoonException ex = assertThrows(OtpResendTooSoonException.class, () -> service.sendOtp(TEL));
        assertTrue(ex.getRemainingSeconds() > 40 && ex.getRemainingSeconds() <= 51);
    }

    @Test
    void codeUtiliseAvecSucces_nouveauCodeImmediat() {
        dernierCode(true, 0);

        assertDoesNotThrow(() -> service.sendOtp(TEL));
    }

    @Test
    void codeEpuiseParTropDEssais_delaiConserve() {
        dernierCode(true, 5);

        assertThrows(OtpResendTooSoonException.class, () -> service.sendOtp(TEL));
    }
}
