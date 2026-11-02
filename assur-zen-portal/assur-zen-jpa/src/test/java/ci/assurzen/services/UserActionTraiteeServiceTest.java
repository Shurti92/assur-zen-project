package ci.assurzen.services;

import ci.assurzen.entities.UserActionTraitee;
import ci.assurzen.repositories.UserActionTraiteeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Liste "A traiter" : actions marquees comme traitees par l'utilisateur connecte (2026-10-03). */
class UserActionTraiteeServiceTest {

    private final UserActionTraiteeRepository repository = mock(UserActionTraiteeRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserActionTraiteeService service = new UserActionTraiteeService(repository, authService);

    @BeforeEach
    void connecter() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(7L, "partenaire", "USER"));
    }

    @Test
    void marquer_enregistreLaCleDeLUtilisateurConnecte() {
        service.marquerTraitee("ECHEANCE:123:2026-10-15");

        ArgumentCaptor<UserActionTraitee> saved = ArgumentCaptor.forClass(UserActionTraitee.class);
        verify(repository).save(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals("ECHEANCE:123:2026-10-15", saved.getValue().getCle());
    }

    @Test
    void marquer_dejaTraitee_rienDePlus() {
        when(repository.existsByUserIdAndCle(7L, "PIECE:9")).thenReturn(true);

        service.marquerTraitee("PIECE:9");

        verify(repository, never()).save(any());
    }

    @Test
    void cleInvalide_400() {
        for (String cle : new String[]{"", "a b", "<script>", "x".repeat(151)}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.marquerTraitee(cle));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Test
    void lister_etAnnuler_limitesAuCompteConnecte() {
        when(repository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                UserActionTraitee.builder().userId(7L).cle("PRIME:4").build()));

        assertEquals(List.of("PRIME:4"), service.mesActionsTraitees());
        service.annuler("PRIME:4");
        verify(repository).deleteByUserIdAndCle(7L, "PRIME:4");
    }
}
