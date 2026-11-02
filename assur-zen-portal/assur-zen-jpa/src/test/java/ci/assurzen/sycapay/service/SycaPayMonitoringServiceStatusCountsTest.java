package ci.assurzen.sycapay.service;

import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.dto.SycaPayStatusCountsResponse;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GET /sycapay/transactions/status-counts (pastilles de filtre du frontend, 2026-09-23). */
class SycaPayMonitoringServiceStatusCountsTest {

    private final SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final SycaPayMonitoringService service = new SycaPayMonitoringService(repository, authService);

    @Test
    void statutsAbsents_valent0_etTotalEstLaSomme() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        Instant debut = Instant.parse("2026-09-01T00:00:00Z");
        when(repository.countByStatus("SYC2026", 42L, null, null, null, debut, null)).thenReturn(List.of(
                new Object[]{SycaPayTransactionStatus.SUCCESS, 2L},
                new Object[]{SycaPayTransactionStatus.FAILED, 1L}));

        SycaPayStatusCountsResponse counts = service.statusCounts("SYC2026", 42L, null, null, null, debut, null);

        assertEquals(new SycaPayStatusCountsResponse(3, 0, 0, 2, 1, 0, 0), counts);
    }

    /** Meme normalisation que search() : une reference vide equivaut a "pas de filtre". */
    @Test
    void referenceVide_estTraiteeCommeAbsente() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(repository.countByStatus(null, null, null, null, null, null, null)).thenReturn(List.of());

        assertEquals(new SycaPayStatusCountsResponse(0, 0, 0, 0, 0, 0, 0), service.statusCounts("  ", null, null, null, null, null, null));
    }

    @Test
    void nonAdmin_renvoie403SansInterrogerLaBase() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "commercial", "COMMERCIAL"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.statusCounts(null, null, null, null, null, null, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).countByStatus(any(), any(), any(), any(), any(), any(), any());
    }
}
