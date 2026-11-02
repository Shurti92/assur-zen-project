package ci.assurzen.services;

import ci.assurzen.dto.CommercialPaymentRequestResponse;
import ci.assurzen.dto.CommercialPaymentRequestsResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.PaymentRequestType;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.exception.CommercialPaymentRequestException;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Demande de paiement de commission du commercial interne (contrat
 * docs/api-contracts/commercial-demande-paiement-commission.md, 2026-09-25).
 */
class CommercialPaymentRequestServiceTest {

    private final CommissionPartialPaymentRequestRepository repository = mock(CommissionPartialPaymentRequestRepository.class);
    private final CommercialRepository commercialRepository = mock(CommercialRepository.class);
    private final CommercialCommissionService commercialCommissionService = mock(CommercialCommissionService.class);
    private final AuthService authService = mock(AuthService.class);
    private final TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);

    private final CommercialPaymentRequestService service = new CommercialPaymentRequestService(
            repository, commercialRepository, commercialCommissionService, authService, telegramNotificationService);

    private Commercial commercial;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(50L);
        commercial = Commercial.builder().id(1L)
                .profile(Profile.builder().id(200L).firstname("Jean").lastname("Koffi").phone("0700000000").user(user).build())
                .build();
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(50L, "0700000000", "COMMERCIAL"));
        when(commercialRepository.findByProfile_UserId(50L)).thenReturn(Optional.of(commercial));
        when(repository.save(any())).thenAnswer(inv -> {
            CommissionPartialPaymentRequest r = inv.getArgument(0);
            r.setId(12L);
            return r;
        });
    }

    /** Commission en attente 5 000, demandes deja accordees : 1 000 pending + 1 000 paid -> 3 000. */
    private void soldeDe5000Moins2000Accordes() {
        when(commercialCommissionService.totalPendingFor(commercial)).thenReturn(5000L);
        when(repository.findByCommercialIdAndStatusIn(eq(1L), any())).thenReturn(List.of(
                demande(10L, 1000L, PartialPaymentRequestStatus.pending),
                demande(11L, 1000L, PartialPaymentRequestStatus.paid)));
    }

    private static CommissionPartialPaymentRequest demande(long id, long montant, PartialPaymentRequestStatus status) {
        return CommissionPartialPaymentRequest.builder().id(id).type(PaymentRequestType.PARTIAL).requestedAmount(montant)
                .status(status).requestedAt(Instant.parse("2026-09-25T10:12:00Z")).build();
    }

    @Test
    void mesDemandes_soldeDisponibleDeduitLesDemandesEnAttenteEtPayees() {
        soldeDe5000Moins2000Accordes();
        when(repository.findByCommercialIdOrderByRequestedAtDesc(1L))
                .thenReturn(List.of(demande(11L, 1000L, PartialPaymentRequestStatus.paid)));

        CommercialPaymentRequestsResponse reponse = service.mesDemandes();

        assertEquals(3000L, reponse.availableBalance());
        assertEquals(1, reponse.history().size());
        assertEquals(PaymentRequestType.PARTIAL, reponse.history().get(0).type());
        assertEquals(PartialPaymentRequestStatus.paid, reponse.history().get(0).status());
    }

    @Test
    void demandeTotale_prendToutLeSoldeDisponibleEtFigeLeTelephone() {
        soldeDe5000Moins2000Accordes();

        CommercialPaymentRequestResponse reponse = service.demander(PaymentRequestType.TOTAL, 999_999L, "cle-1");

        ArgumentCaptor<CommissionPartialPaymentRequest> captor = ArgumentCaptor.forClass(CommissionPartialPaymentRequest.class);
        verify(repository).save(captor.capture());
        CommissionPartialPaymentRequest saved = captor.getValue();
        assertEquals(3000L, saved.getRequestedAmount());
        assertEquals(0L, saved.getPendingBalanceAfter());
        assertEquals(commercial, saved.getCommercial());
        assertEquals(null, saved.getPartenaire());
        assertEquals("0700000000", saved.getNumeroWaveCommissionSnapshot());
        assertEquals(PaymentRequestType.TOTAL, reponse.type());
        assertEquals(PartialPaymentRequestStatus.pending, reponse.status());
        verify(repository).lockToutesPourCommercial(1L);
        verify(telegramNotificationService).envoyer(any());
    }

    @Test
    void demandePartielle_montantLibreSansPlafondEnPourcentage() {
        soldeDe5000Moins2000Accordes();

        CommercialPaymentRequestResponse reponse = service.demander(PaymentRequestType.PARTIAL, 2900L, null);

        assertEquals(2900L, reponse.requestedAmount());
    }

    @Test
    void demandePartielle_superieureAuSolde_estRefuseeEn422() {
        soldeDe5000Moins2000Accordes();

        CommercialPaymentRequestException ex = assertThrows(CommercialPaymentRequestException.class,
                () -> service.demander(PaymentRequestType.PARTIAL, 3001L, "cle-2"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("AMOUNT_EXCEEDS_BALANCE", ex.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void demandePartielle_montantAbsentOuNul_estRefuseeEn422() {
        for (Long montant : new Long[]{null, 0L, -5L}) {
            CommercialPaymentRequestException ex = assertThrows(CommercialPaymentRequestException.class,
                    () -> service.demander(PaymentRequestType.PARTIAL, montant, "cle-3"));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
            assertEquals("INVALID_AMOUNT", ex.getCode());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void soldeNul_estRefuseEn409() {
        when(commercialCommissionService.totalPendingFor(commercial)).thenReturn(2000L);
        when(repository.findByCommercialIdAndStatusIn(eq(1L), any()))
                .thenReturn(List.of(demande(10L, 2000L, PartialPaymentRequestStatus.paid)));

        CommercialPaymentRequestException ex = assertThrows(CommercialPaymentRequestException.class,
                () -> service.demander(PaymentRequestType.TOTAL, null, "cle-4"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("NOTHING_TO_PAY", ex.getCode());
    }

    @Test
    void rejeuDeLaMemeCle_renvoieLaDemandeDejaCreeeSansEnCreerUneAutre() {
        CommissionPartialPaymentRequest existante = demande(12L, 1000L, PartialPaymentRequestStatus.pending);
        when(repository.findByCommercialIdAndIdempotencyKey(1L, "cle-5")).thenReturn(Optional.of(existante));

        CommercialPaymentRequestResponse reponse = service.demander(PaymentRequestType.PARTIAL, 1000L, "cle-5");

        assertEquals(12L, reponse.id());
        verify(repository, never()).save(any());
    }

    @Test
    void memeCleAvecUnAutreMontant_estRefuseeEn409() {
        when(repository.findByCommercialIdAndIdempotencyKey(1L, "cle-6"))
                .thenReturn(Optional.of(demande(12L, 1000L, PartialPaymentRequestStatus.pending)));

        CommercialPaymentRequestException ex = assertThrows(CommercialPaymentRequestException.class,
                () -> service.demander(PaymentRequestType.PARTIAL, 1500L, "cle-6"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void appelantSansLeRoleCommercial_estRefuseEn403() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, service::mesDemandes);

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
