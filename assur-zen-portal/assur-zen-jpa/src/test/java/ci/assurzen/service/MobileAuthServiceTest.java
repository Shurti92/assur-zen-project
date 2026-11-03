package ci.assurzen.service;

import ci.assurzen.dto.AuthTokens;
import ci.assurzen.dto.LoginResponse;
import ci.assurzen.dto.MobileAuthDtos;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.User;
import ci.assurzen.exception.InvalidVerificationTokenException;
import ci.assurzen.exception.PhoneAlreadyRegisteredException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot 1 mobile (2026-11-02) : inscription, rapprochement des assures, codes SMS, force du PIN. */
class MobileAuthServiceTest {

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private InsuredRepository insuredRepository;
    private ClientAccountInsuredRepository clientAccountInsuredRepository;
    private OtpService otpService;
    private JwtTokenService jwtTokenService;
    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private MobileAuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        insuredRepository = mock(InsuredRepository.class);
        clientAccountInsuredRepository = mock(ClientAccountInsuredRepository.class);
        otpService = mock(OtpService.class);
        jwtTokenService = mock(JwtTokenService.class);
        authService = mock(AuthService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        CodeGeneratorService codes = mock(CodeGeneratorService.class);
        when(codes.generate(any())).thenReturn("PRF2026000001");
        when(passwordEncoder.encode(any())).thenReturn("hash");
        service = new MobileAuthService(userRepository, profileRepository, mock(UserSessionRepository.class), insuredRepository,
                clientAccountInsuredRepository, mock(AccountDeletionRequestRepository.class), otpService, jwtTokenService,
                passwordEncoder, mock(PasswordHistoryService.class), codes, mock(UserActiveStatusCache.class), authService);
    }

    private MobileAuthDtos.RegisterRequest inscription(String pin) {
        return new MobileAuthDtos.RegisterRequest("Awa Koné", "0700000001", pin, "jeton");
    }

    @Test
    void inscription_creeUnCompteClientEtRattacheLesAssuresDuMemeNumero() {
        when(userRepository.findByUsername("0700000001")).thenReturn(Optional.empty());
        when(jwtTokenService.consommerJetonVerification("jeton", "0700000001", "REGISTER")).thenReturn(true);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });
        Insured assure = new Insured();
        assure.setId(5L);
        when(insuredRepository.findByPhoneNormalise("0700000001")).thenReturn(List.of(assure));
        when(clientAccountInsuredRepository.existsByUserIdAndInsuredId(77L, 5L)).thenReturn(false);
        when(authService.ouvrirSession(any(User.class))).thenReturn(
                new LoginResponse(null, new AuthTokens("a", "r", "Bearer", 3600)));

        MobileAuthDtos.RegisterResponse reponse = service.inscrire(inscription("739104"));

        assertTrue(reponse.linkedInsured());
        assertEquals(1, reponse.linkedCount());
        assertEquals("a", reponse.tokens().accessToken());
        verify(clientAccountInsuredRepository).save(any());
    }

    @Test
    void inscription_sansAssureCorrespondant_neRattacheRien() {
        when(userRepository.findByUsername("0700000001")).thenReturn(Optional.empty());
        when(jwtTokenService.consommerJetonVerification(any(), any(), any())).thenReturn(true);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(78L);
            return u;
        });
        when(insuredRepository.findByPhoneNormalise(any())).thenReturn(List.of());
        when(authService.ouvrirSession(any(User.class))).thenReturn(
                new LoginResponse(null, new AuthTokens("a", "r", "Bearer", 3600)));

        MobileAuthDtos.RegisterResponse reponse = service.inscrire(inscription("739104"));

        assertFalse(reponse.linkedInsured());
        assertEquals(0, reponse.linkedCount());
    }

    @Test
    void inscription_pinTropFaible_estRefuseSansConsommerLeJeton() {
        assertThrows(PinTooWeakException.class, () -> service.inscrire(inscription("123456")));
        verify(jwtTokenService, never()).consommerJetonVerification(any(), any(), any());
    }

    @Test
    void inscription_numeroDejaInscrit_donne409() {
        when(userRepository.findByUsername("0700000001")).thenReturn(Optional.of(new User()));
        assertThrows(PhoneAlreadyRegisteredException.class, () -> service.inscrire(inscription("739104")));
    }

    @Test
    void inscription_jetonInvalide_estRefuse() {
        when(userRepository.findByUsername("0700000001")).thenReturn(Optional.empty());
        when(jwtTokenService.consommerJetonVerification(any(), any(), any())).thenReturn(false);
        assertThrows(InvalidVerificationTokenException.class, () -> service.inscrire(inscription("739104")));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void demandeDeCode_resetPinSurNumeroInconnu_neEnvoieAucunSms() {
        when(userRepository.findByUsername("0700000009")).thenReturn(Optional.empty());
        service.demanderCode("0700000009", "RESET_PIN");
        verify(otpService, never()).sendOtp(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void demandeDeCode_registerSurNumeroExistant_donne409SansSms() {
        when(userRepository.findByUsername("0700000001")).thenReturn(Optional.of(new User()));
        assertThrows(PhoneAlreadyRegisteredException.class, () -> service.demanderCode("0700000001", "REGISTER"));
        verify(otpService, never()).sendOtp(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void demandeDeCode_registerNumeroLibre_verifieLeQuotaPuisEnvoie() {
        when(userRepository.findByUsername("0700000002")).thenReturn(Optional.empty());
        service.demanderCode("0700000002", "REGISTER");
        verify(otpService).verifierQuota("0700000002");
        verify(otpService).sendOtpDemande("0700000002", "REGISTER");
    }

    @Test
    void pinPolicy_refuseLesPinTriviauxEtAccepteLesAutres() {
        for (String faible : new String[]{"111111", "000000", "123456", "654321", "234567", "123123", "111112", "112233"}) {
            assertTrue(PinPolicy.estTropFaible(faible), faible);
        }
        for (String correct : new String[]{"739104", "805216", "905271", "246801"}) {
            assertFalse(PinPolicy.estTropFaible(correct), correct);
        }
        assertTrue(PinPolicy.estTropFaible("12345"));
        assertTrue(PinPolicy.estTropFaible("12ab56"));
    }
}
