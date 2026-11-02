package ci.assurzen.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.User;
import ci.assurzen.entities.UserSession;
import ci.assurzen.exception.InvalidRefreshTokenException;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.JwtTokenService;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.IpLoginAttemptService;
import ci.assurzen.services.LoginAttemptService;
import ci.assurzen.services.LoginHistoryService;
import ci.assurzen.services.OtpService;
import ci.assurzen.services.PasswordHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verrouillage du renouvellement des jetons (2026-10-04). */
class AuthServiceRefreshTest {

    private static final String JETON = "refresh-jeton";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final DynamicConfigurationService configuration = mock(DynamicConfigurationService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final OtpService otpService = mock(OtpService.class);
    private final AuthService service = new AuthService(userRepository, profileRepository,
            mock(PartenaireRepository.class), sessionRepository, mock(CodeGeneratorService.class), jwtTokenService,
            mock(PasswordEncoder.class), otpService, configuration, mock(FunctionalAuditRecorder.class),
            mock(LoginAttemptService.class), mock(LoginHistoryService.class), mock(IpLoginAttemptService.class),
            mock(PasswordHistoryService.class));

    @BeforeEach
    void setUp() {
        when(jwtTokenService.parseAndValidate(JETON)).thenReturn(new JwtTokenService.JwtClaims(7L, "u", "refresh",
                "jti-r", "USER", null, Instant.now().plusSeconds(3600), null));
        User user = new User();
        user.setId(7L);
        user.setActive(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(configuration.getLong(anyString(), anyLong())).thenReturn(3600L);
        when(jwtTokenService.generateAccessToken(any(), anyLong(), anyString())).thenReturn("access");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("nouveau-refresh");
        when(jwtTokenService.parseAndValidate("access")).thenReturn(new JwtTokenService.JwtClaims(7L, "u", "access",
                "jti-a", "USER", null, Instant.now().plusSeconds(3600), "S1"));
    }

    private UserSession session(boolean revoquee, Instant consommeLe) {
        UserSession s = UserSession.builder().id(1L).userId(7L).token(JETON).sessionId("S1").revoked(revoquee)
                .rotatedAt(consommeLe).createdAt(Instant.now().minusSeconds(60)).build();
        when(sessionRepository.findByTokenForUpdate(JETON)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void jetonActif_consommeSousVerrou_nouvelleLigneDeLaMemeSession() {
        UserSession s = session(false, null);

        assertNotNull(service.refresh(JETON));

        assertTrue(s.isRevoked());
        assertNotNull(s.getRotatedAt());
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository, atLeastOnce()).save(saved.capture());
        assertEquals("S1", saved.getValue().getSessionId());
        verify(sessionRepository, never()).findByToken(any());
    }

    @Test
    void renouvellementSimultane_jetonConsommeIlYAUnInstant_tolere() {
        session(true, Instant.now().minusSeconds(2));
        when(sessionRepository.existsBySessionIdAndRevokedFalse("S1")).thenReturn(true);

        assertNotNull(service.refresh(JETON));
    }

    @Test
    void jetonConsommeIlYALongtemps_refuse() {
        session(true, Instant.now().minusSeconds(60));
        when(sessionRepository.existsBySessionIdAndRevokedFalse("S1")).thenReturn(true);

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(JETON));
    }

    @Test
    void sessionDeconnecteeEntreTemps_refuse() {
        session(true, Instant.now().minusSeconds(2));
        when(sessionRepository.existsBySessionIdAndRevokedFalse("S1")).thenReturn(false);

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(JETON));
    }

    @Test
    void jetonRevoqueParDeconnexion_jamaisTolere() {
        session(true, null);

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(JETON));
        verify(sessionRepository, never()).existsBySessionIdAndRevokedFalse(eq("S1"));
    }

    @Test
    void motDePasseOublie_renvoiTropRapproche_silencieux_etSansTransactionEnglobante() throws Exception {
        User user = userRepository.findById(7L).orElseThrow();
        user.setUsername("0000000104");
        when(userRepository.findByUsername("0000000104")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(
                ci.assurzen.entities.Profile.builder().id(70L).phone("0000000104").build()));
        org.mockito.Mockito.doThrow(new ci.assurzen.exception.OtpResendTooSoonException(42))
                .when(otpService).sendOtp("0000000104");

        service.forgotPassword(new ci.assurzen.dto.ForgotPasswordRequest("0000000104"));

        // Une @Transactional sur forgotPassword ferait echouer le commit (rollback-only) -> 500 en production.
        assertEquals(null, AuthService.class.getMethod("forgotPassword", ci.assurzen.dto.ForgotPasswordRequest.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}
