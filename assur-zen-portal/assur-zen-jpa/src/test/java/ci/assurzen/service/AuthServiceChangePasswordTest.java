package ci.assurzen.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.ChangePasswordRequest;
import ci.assurzen.entities.User;
import ci.assurzen.exception.InvalidCurrentPasswordException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Changement de mot de passe : mot de passe actuel incorrect = 400 code stable, aucune session revoquee (2026-10-05). */
class AuthServiceChangePasswordTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordHistoryService passwordHistory = mock(PasswordHistoryService.class);
    private final AuthService service = new AuthService(userRepository, mock(ProfileRepository.class),
            mock(PartenaireRepository.class), sessionRepository, mock(CodeGeneratorService.class), mock(JwtTokenService.class),
            passwordEncoder, mock(OtpService.class), mock(DynamicConfigurationService.class), mock(FunctionalAuditRecorder.class),
            mock(LoginAttemptService.class), mock(LoginHistoryService.class), mock(IpLoginAttemptService.class), passwordHistory);

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void motDePasseActuelFaux_exceptionDediee_sansRevocationNiHistorique() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "0700000007", "USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        User user = new User();
        user.setId(7L);
        user.setPassword("hash-actuel");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("faux", "hash-actuel")).thenReturn(false);

        InvalidCurrentPasswordException ex = assertThrows(InvalidCurrentPasswordException.class,
                () -> service.changePassword(new ChangePasswordRequest("faux", "NouveauMdp!2026"), null));

        assertEquals("Mot de passe actuel incorrect", ex.getMessage());
        assertEquals("INVALID_CURRENT_PASSWORD", InvalidCurrentPasswordException.CODE);
        verify(sessionRepository, never()).saveAll(any());
        verify(passwordHistory, never()).record(any(), any());
    }
}
