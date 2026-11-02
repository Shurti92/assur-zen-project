package ci.assurzen.services;

import ci.assurzen.dto.CommissionStatementResponse;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.Profile;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Releves mensuels des commissions versees (2026-10-03). */
class CommissionStatementServiceTest {

    private final CommissionPaymentRepository paymentRepository = mock(CommissionPaymentRepository.class);
    private final CommissionPartialPaymentRequestRepository requestRepository = mock(CommissionPartialPaymentRequestRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final CommissionStatementService service = new CommissionStatementService(paymentRepository, requestRepository,
            partenaireRepository, profileRepository, authService);

    private final Partenaire partenaire = Partenaire.builder().id(9L).nom("Tanoh").codePartenaire("PART-9").build();

    @BeforeEach
    void connecterLePartenaire() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(90L, "0700000009", "USER"));
        when(profileRepository.findByUserId(90L)).thenReturn(Optional.of(Profile.builder().id(900L).build()));
        when(partenaireRepository.findByProfile_Id(900L)).thenReturn(List.of(partenaire));
        when(partenaireRepository.findById(9L)).thenReturn(Optional.of(partenaire));

        when(paymentRepository.findByPartenaireIdOrderByPaidAtAsc(9L)).thenReturn(List.of(
                paiement(1L, 1_500L, 1_000L, "2026-09-27T10:00:00Z"),
                paiement(2L, 1_000L, null, "2026-09-27T10:00:00Z"),
                paiement(3L, 2_000L, null, "2026-08-10T09:00:00Z")));
        when(requestRepository.findByPartenaireIdOrderByRequestedAtDesc(9L)).thenReturn(List.of(
                demande(5L, 7_200L, PartialPaymentRequestStatus.paid, "2026-09-05T08:00:00Z"),
                demande(6L, 3_000L, PartialPaymentRequestStatus.failed, null)));
    }

    @Test
    void lister_unReleveParMoisDuPlusRecentAuPlusAncien_montantReellementVerse() {
        List<CommissionStatementResponse> releves = service.lister(9L);

        assertEquals(2, releves.size());
        CommissionStatementResponse septembre = releves.get(0);
        assertEquals("2026-09", septembre.mois());
        assertEquals(2L, septembre.nbContrats());
        assertEquals(2_500L, septembre.montantCommissions());
        assertEquals(1_000L, septembre.avancesImputees());
        assertEquals(7_200L, septembre.avancesVersees());
        assertEquals(8_700L, septembre.montantVerse());
        assertEquals(Instant.parse("2026-09-27T10:00:00Z"), septembre.dateVersement());
        assertEquals("CLOTURE", septembre.statut());
        assertNull(septembre.lignes());
        assertEquals("2026-08", releves.get(1).mois());
        assertEquals(2_000L, releves.get(1).montantVerse());
    }

    @Test
    void detail_lignesTrieesParDate_etPdfGenere() {
        CommissionStatementResponse septembre = service.detail(9L, "2026-09");

        assertEquals(3, septembre.lignes().size());
        assertEquals("AVANCE", septembre.lignes().get(0).type());
        assertEquals("CTR-1", septembre.lignes().get(1).policyNumber());
        assertEquals(500L, septembre.lignes().get(1).montantVerse());

        byte[] pdf = service.pdf(9L, "2026-09");
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void detail_moisSansVersement404_moisInvalide400() {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.detail(9L, "2026-07")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.detail(9L, "septembre")).getStatusCode());
    }

    @Test
    void autrePartenaire_403() {
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.lister(10L)).getStatusCode());
    }

    private static CommissionPayment paiement(long contratId, long montant, Long avance, String date) {
        Contract contrat = Contract.builder().id(contratId).policyNumber("CTR-" + contratId).build();
        return CommissionPayment.builder().id(contratId).contract(contrat).amount(montant).avanceImputee(avance)
                .paidAt(Instant.parse(date)).build();
    }

    private static CommissionPartialPaymentRequest demande(long id, long montant, PartialPaymentRequestStatus statut, String validee) {
        return CommissionPartialPaymentRequest.builder().id(id).requestedAmount(montant).status(statut)
                .validatedAt(validee == null ? null : Instant.parse(validee)).build();
    }
}
