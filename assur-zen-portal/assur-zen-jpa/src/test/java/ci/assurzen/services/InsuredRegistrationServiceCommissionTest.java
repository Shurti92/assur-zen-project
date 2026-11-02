package ci.assurzen.services;

import ci.assurzen.dto.ContractRequest;
import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.ContractSubscriptionRequest;
import ci.assurzen.dto.InsuredRegistrationRequest;
import ci.assurzen.dto.InsuredRequest;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.entities.StatutReversementPrime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test 5 (decision utilisateur du 2026-09-25) : la souscription en masse transmet a chaque contrat la
 * commission partenaire saisie, sans reinitialisation ni recalcul. */
class InsuredRegistrationServiceCommissionTest {

    @Test
    void test5_creationEnMasse_500Contrats_commission2500_transmiseTelleQuelleAChaqueContrat() {
        InsuredService insuredService = mock(InsuredService.class);
        ContractsService contractsService = mock(ContractsService.class);
        InsuredRegistrationService service = new InsuredRegistrationService(insuredService, contractsService, mock(SmsNotificationService.class));

        InsuredResponse assure = mock(InsuredResponse.class);
        when(assure.id()).thenReturn(10L);
        when(insuredService.create(any())).thenReturn(assure);
        when(contractsService.create(any())).thenReturn(mock(ContractResponse.class));

        List<ContractSubscriptionRequest> souscriptions = IntStream.range(0, 500)
                .mapToObj(i -> new ContractSubscriptionRequest(null, null, "12 mois", null, 75L, 7L, 2L, 4500L,
                        null, 2000L, 2500L, null, null, null, 3L, null, null, null, null, null, null, null, null))
                .toList();

        InsuredRequest nouvelAssure = mock(InsuredRequest.class);
        when(nouvelAssure.insuredId()).thenReturn(null);
        service.register(new InsuredRegistrationRequest(nouvelAssure, souscriptions));

        ArgumentCaptor<ContractRequest> captor = ArgumentCaptor.forClass(ContractRequest.class);
        verify(contractsService, times(500)).create(captor.capture());
        assertEquals(500, captor.getAllValues().size());
        assertTrue(captor.getAllValues().stream().allMatch(r -> Long.valueOf(2500L).equals(r.partnerShare())));
        assertTrue(captor.getAllValues().stream().allMatch(r -> Long.valueOf(2000L).equals(r.commissionAmount())));
    }

    /** « Prime versee ? » a la souscription (2026-09-25) : primeReversementStatus transmis au contrat,
     * null conserve le comportement par defaut (non_reverse a la creation). */
    @Test
    void souscription_transmetLeStatutDeReversementDeLaPrime() {
        InsuredService insuredService = mock(InsuredService.class);
        ContractsService contractsService = mock(ContractsService.class);
        InsuredRegistrationService service = new InsuredRegistrationService(insuredService, contractsService, mock(SmsNotificationService.class));
        InsuredResponse assure = mock(InsuredResponse.class);
        when(assure.id()).thenReturn(10L);
        when(insuredService.create(any())).thenReturn(assure);
        when(contractsService.create(any())).thenReturn(mock(ContractResponse.class));
        InsuredRequest nouvelAssure = mock(InsuredRequest.class);
        when(nouvelAssure.insuredId()).thenReturn(null);

        service.register(new InsuredRegistrationRequest(nouvelAssure, List.of(
                souscription(StatutReversementPrime.reverse), souscription(null))));

        ArgumentCaptor<ContractRequest> captor = ArgumentCaptor.forClass(ContractRequest.class);
        verify(contractsService, times(2)).create(captor.capture());
        assertEquals(StatutReversementPrime.reverse, captor.getAllValues().get(0).primeReversementStatus());
        assertEquals(null, captor.getAllValues().get(1).primeReversementStatus());
    }

    private static ContractSubscriptionRequest souscription(StatutReversementPrime statut) {
        return new ContractSubscriptionRequest(null, null, "12 mois", null, 3L, 12L, 2L, 150000L,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, statut);
    }
}
