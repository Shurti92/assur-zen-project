package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.MaSessionResponse;
import ci.assurzen.entities.DeviceType;
import ci.assurzen.entities.LoginHistory;
import ci.assurzen.entities.UserSession;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.security.JwtTokenService;
import ci.assurzen.security.TokenRevocationService;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Parametres > Securite : appareils connectes et signalement (2026-10-03). */
class SecuriteCompteServiceTest {

    private static final String JETON = "jeton-courant";

    private final AuthService authService = mock(AuthService.class);
    private final LoginHistoryService loginHistoryService = mock(LoginHistoryService.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);
    private final TelegramNotificationService telegram = mock(TelegramNotificationService.class);
    private final FunctionalAuditRecorder audit = mock(FunctionalAuditRecorder.class);
    private final SecuriteCompteService service = new SecuriteCompteService(authService, loginHistoryService,
            sessionRepository, jwtTokenService, revocationService, telegram, audit);

    private UserSession courante;
    private UserSession telephone;
    private UserSession ancienne;

    @BeforeEach
    void setUp() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(7L, "0700000007", "USER"));
        when(jwtTokenService.getRefreshTtlSeconds()).thenReturn(604_800L);
        when(jwtTokenService.parseAndValidate(JETON)).thenReturn(new JwtTokenService.JwtClaims(7L, "0700000007", "access",
                "jti-1", "USER", null, Instant.now().plusSeconds(3600), "S-COURANTE"));
        Instant maintenant = Instant.now();
        courante = UserSession.builder().id(1L).userId(7L).sessionId("S-COURANTE").deviceType(DeviceType.DESKTOP)
                .browser("Edge").createdAt(maintenant.minusSeconds(60)).lastActivityAt(maintenant.minusSeconds(60))
                .accessJti("jti-1").accessExpiresAt(maintenant.plusSeconds(3500)).build();
        telephone = UserSession.builder().id(2L).userId(7L).sessionId("S-TEL").deviceType(DeviceType.MOBILE)
                .browser("Chrome").city("Abidjan").createdAt(maintenant.minusSeconds(600)).lastActivityAt(maintenant.minusSeconds(600))
                .accessJti("jti-2").accessExpiresAt(maintenant.plusSeconds(3000)).build();
        ancienne = UserSession.builder().id(3L).userId(7L).createdAt(maintenant.minusSeconds(3600)).build();
        UserSession expiree = UserSession.builder().id(4L).userId(7L).sessionId("S-EXPIREE")
                .createdAt(maintenant.minusSeconds(700_000L)).build();
        when(sessionRepository.findAllByUserIdAndRevokedFalse(7L)).thenReturn(List.of(courante, telephone, ancienne, expiree));
    }

    @Test
    void mesSessions_actives_courantePremiere_ancienneAvecIdLegacy() {
        List<MaSessionResponse> sessions = service.mesSessions(JETON);

        assertEquals(List.of("S-COURANTE", "S-TEL", "L3"), sessions.stream().map(MaSessionResponse::id).toList());
        assertTrue(sessions.get(0).courante());
        assertFalse(sessions.get(1).courante());
        assertEquals("Abidjan", sessions.get(1).ville());
    }

    @Test
    void mesSessions_renouvellementsConcurrents_uneSeuleLigneParSession_etRevocationDeTous() {
        Instant maintenant = Instant.now();
        UserSession doublon = UserSession.builder().id(5L).userId(7L).sessionId("S-TEL").deviceType(DeviceType.MOBILE)
                .browser("Chrome").createdAt(maintenant.minusSeconds(30)).lastActivityAt(maintenant.minusSeconds(30))
                .accessJti("jti-5").accessExpiresAt(maintenant.plusSeconds(3500)).build();
        when(sessionRepository.findAllByUserIdAndRevokedFalse(7L)).thenReturn(List.of(courante, telephone, doublon, ancienne));

        List<MaSessionResponse> sessions = service.mesSessions(JETON);

        assertEquals(List.of("S-TEL", "S-COURANTE", "L3"), sessions.stream().map(MaSessionResponse::id).toList());
        assertEquals(1, sessions.stream().filter(MaSessionResponse::courante).count());
        assertEquals(2, service.revoquerAutresSessions(JETON));
        assertTrue(telephone.isRevoked());
        assertTrue(doublon.isRevoked());
        verify(revocationService).revoquer("jti-5", doublon.getAccessExpiresAt());
    }

    @Test
    void revoquerAutres_conserveLaCourante_etRevoqueLesAccessTokensImmediatement() {
        int count = service.revoquerAutresSessions(JETON);

        assertEquals(2, count);
        assertFalse(courante.isRevoked());
        assertTrue(telephone.isRevoked());
        assertTrue(ancienne.isRevoked());
        verify(revocationService).revoquer("jti-2", telephone.getAccessExpiresAt());
        verify(revocationService, never()).revoquer("jti-1", courante.getAccessExpiresAt());
    }

    @Test
    void revoquerSession_inconnue404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.revoquerSession("S-AUTRE-COMPTE"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

        service.revoquerSession("L3");
        assertTrue(ancienne.isRevoked());
    }

    @Test
    void revoquerAutres_jetonSansSession_409() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.revoquerAutresSessions(null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void signaler_alerteLesAdminsUneSeuleFois() {
        when(loginHistoryService.signaler(7L, 50L)).thenReturn(true, false);
        when(loginHistoryService.getMienne(7L, 50L)).thenReturn(LoginHistory.builder().id(50L).userId(7L)
                .createdAt(Instant.parse("2026-10-03T08:00:00Z")).ipAddress("41.202.1.1").city("Bouake").echec(true).build());

        service.signalerConnexion(50L);
        service.signalerConnexion(50L);

        verify(telegram).envoyer(anyString());
        verify(audit).recordAction(any(), any(), any(), any(), any());
    }
}
