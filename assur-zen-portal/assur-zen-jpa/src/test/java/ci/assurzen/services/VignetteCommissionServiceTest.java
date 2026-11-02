package ci.assurzen.services;

import ci.assurzen.dto.VignetteCommissionPaymentRequest;
import ci.assurzen.dto.VignetteCommissionResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VignetteCommission;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VignetteCommissionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le paiement du registre de commission apporteur vignette (§ "commission apporteur vignette",
 * 2026-09-15, contenu SMS revu le 2026-09-16) : rejet d'une commission deja payee, UN SMS PAR VIGNETTE
 * (jamais agrege, meme quand plusieurs commissions du meme apporteur sont payees dans le meme appel) -
 * le contenu inclut les details de la moto concernee.
 */
class VignetteCommissionServiceTest {

    private record Fixture(
            VignetteCommissionService service,
            VignetteCommissionRepository repository,
            UserRepository userRepository,
            AuthService authService,
            SmsNotificationService smsNotificationService,
            TelegramNotificationService telegramNotificationService
    ) {
    }

    private Fixture newFixture() {
        VignetteCommissionRepository repository = mock(VignetteCommissionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuthService authService = mock(AuthService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        VignetteCommissionService service = new VignetteCommissionService(repository, userRepository, authService,
                smsNotificationService, telegramNotificationService);

        return new Fixture(service, repository, userRepository, authService, smsNotificationService, telegramNotificationService);
    }

    private DemandeVignetteMoto demande(long id, String reference) {
        return DemandeVignetteMoto.builder().id(id).reference(reference).typeDemande(TypeDemandeVignette.NOUVELLE_DEMANDE)
                .marqueMoto("Yamaha").modeleMoto("Crypton").numeroChassisMoto("CH-" + id).build();
    }

    private VignetteCommission commissionPartenaire(long id, Partenaire partenaire, long montant, CommissionStatus statut) {
        return VignetteCommission.builder().id(id).demandeVignetteMoto(demande(id, "VGN-" + id))
                .partenaire(partenaire).montant(montant).statut(statut).build();
    }

    private VignetteCommission commissionCommercial(long id, Commercial commercial, long montant, CommissionStatus statut) {
        return VignetteCommission.builder().id(id).demandeVignetteMoto(demande(id, "VGN-" + id))
                .commercial(commercial).montant(montant).statut(statut).build();
    }

    private Partenaire partenaire(long id) {
        return Partenaire.builder().id(id).nom("Yao").prenoms("Marie").telephone("0700000001").build();
    }

    private Commercial commercial(long id) {
        Profile profile = Profile.builder().id(id).firstname("Jean").lastname("Koffi").phone("0700000002").build();
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).profile(profile).build();
    }

    // ---------------------------------------------------------------- payer()

    @Test
    void payer_partenaire_succes_marqueLaCommissionPayeeEtEnvoieLeSmsAvecLesDetailsDeLaMoto() {
        Fixture fx = newFixture();
        Partenaire p = partenaire(5L);
        VignetteCommission commission = commissionPartenaire(1L, p, 1000L, CommissionStatus.pending);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(commission));
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000001"), eq("COMMISSION_VIGNETTE_PAYEE"), any(), any()))
                .thenReturn(true);

        List<VignetteCommissionResponse> responses = fx.service().payer(new VignetteCommissionPaymentRequest(List.of(1L), "REF-1"));

        assertEquals(1, responses.size());
        assertEquals(0, Long.compare(1000L, responses.get(0).montant()));
        assertEquals(Boolean.TRUE, responses.get(0).notificationEnvoyee());
        assertEquals(CommissionStatus.paid, commission.getStatut());
        assertEquals("REF-1", commission.getReference());
        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("COMMISSION_VIGNETTE_PAYEE"),
                eq(java.util.Map.of("montant", "1 000", "marque", "Yamaha", "modele", "Crypton",
                        "numeroChassis", "CH-1", "reference", "REF-1")),
                eq(ci.assurzen.entities.TypeMessageSms.commission_vignette_payee));
        verify(fx.telegramNotificationService()).envoyer(any());
    }

    @Test
    void payer_commercial_succes_resoutLeTelephoneDepuisLeProfil() {
        Fixture fx = newFixture();
        Commercial c = commercial(9L);
        VignetteCommission commission = commissionCommercial(2L, c, 1000L, CommissionStatus.pending);
        when(fx.repository().findById(2L)).thenReturn(Optional.of(commission));
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000002"), eq("COMMISSION_VIGNETTE_PAYEE"), any(), any()))
                .thenReturn(true);

        List<VignetteCommissionResponse> responses = fx.service().payer(new VignetteCommissionPaymentRequest(List.of(2L), "REF-2"));

        assertEquals(Boolean.TRUE, responses.get(0).notificationEnvoyee());
        assertEquals(CommissionStatus.paid, commission.getStatut());
    }

    @Test
    void payer_deuxCommissionsMemePartenaire_envoieDeuxSmsDistinctsUnParVignette() {
        Fixture fx = newFixture();
        Partenaire p = partenaire(5L);
        VignetteCommission c1 = commissionPartenaire(1L, p, 1000L, CommissionStatus.pending);
        VignetteCommission c2 = commissionPartenaire(2L, p, 1000L, CommissionStatus.pending);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c1));
        when(fx.repository().findById(2L)).thenReturn(Optional.of(c2));

        fx.service().payer(new VignetteCommissionPaymentRequest(List.of(1L, 2L), "REF-3"));

        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("COMMISSION_VIGNETTE_PAYEE"),
                eq(java.util.Map.of("montant", "1 000", "marque", "Yamaha", "modele", "Crypton",
                        "numeroChassis", "CH-1", "reference", "REF-3")),
                eq(ci.assurzen.entities.TypeMessageSms.commission_vignette_payee));
        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("COMMISSION_VIGNETTE_PAYEE"),
                eq(java.util.Map.of("montant", "1 000", "marque", "Yamaha", "modele", "Crypton",
                        "numeroChassis", "CH-2", "reference", "REF-3")),
                eq(ci.assurzen.entities.TypeMessageSms.commission_vignette_payee));
        verify(fx.telegramNotificationService(), org.mockito.Mockito.times(2)).envoyer(any());
    }

    @Test
    void payer_commissionDejaPayee_estRejeteAvec409() {
        Fixture fx = newFixture();
        VignetteCommission commission = commissionPartenaire(1L, partenaire(5L), 1000L, CommissionStatus.paid);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(commission));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().payer(new VignetteCommissionPaymentRequest(List.of(1L), null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.repository(), never()).saveAll(any());
    }

    @Test
    void payer_commissionIntrouvable_leve404() {
        Fixture fx = newFixture();
        when(fx.repository().findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().payer(new VignetteCommissionPaymentRequest(List.of(99L), null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
