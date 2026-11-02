package ci.assurzen.webpush.service;

import ci.assurzen.entities.User;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.webpush.dto.PushSubscriptionRequest;
import ci.assurzen.webpush.entity.WebPushSubscription;
import ci.assurzen.webpush.repository.WebPushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Abonnements Web Push (2026-09-26) : toujours rattaches a l'utilisateur du JWT, upsert par endpoint,
 * un utilisateur ne peut pas desabonner le navigateur d'un autre. */
class WebPushSubscriptionServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";

    private final WebPushSubscriptionRepository repository = mock(WebPushSubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final WebPushSubscriptionService service = new WebPushSubscriptionService(
            repository, userRepository, authService, mock(WebPushNotificationService.class));

    private final User utilisateurA = utilisateur(70);
    private final User utilisateurB = utilisateur(71);

    private static User utilisateur(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private void connecte(User user) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(user.getId(), "u" + user.getId(), "PARTENAIRE"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private static PushSubscriptionRequest requete() {
        return new PushSubscriptionRequest(ENDPOINT, null, new PushSubscriptionRequest.Keys("cle-p256dh", "cle-auth"));
    }

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void premierAbonnement_rattacheALUtilisateurDuJwt() {
        connecte(utilisateurA);
        when(repository.findByEndpointHash(any())).thenReturn(Optional.empty());

        service.enregistrer(requete(), "Mozilla/5.0");

        ArgumentCaptor<WebPushSubscription> captor = ArgumentCaptor.forClass(WebPushSubscription.class);
        verify(repository).save(captor.capture());
        assertEquals(utilisateurA, captor.getValue().getUser());
        assertEquals(WebPushSubscriptionService.hash(ENDPOINT), captor.getValue().getEndpointHash());
        assertTrue(captor.getValue().isActive());
        assertEquals("cle-p256dh", captor.getValue().getP256dh());
    }

    @Test
    void endpointDejaConnu_misAJourReactiveEtReattribueAuCompteConnecte() {
        WebPushSubscription existant = WebPushSubscription.builder().id(5L).user(utilisateurA).endpoint(ENDPOINT)
                .endpointHash(WebPushSubscriptionService.hash(ENDPOINT)).p256dh("ancienne").auth("ancienne")
                .active(false).failureCount(3).build();
        when(repository.findByEndpointHash(WebPushSubscriptionService.hash(ENDPOINT))).thenReturn(Optional.of(existant));
        connecte(utilisateurB);

        service.enregistrer(requete(), null);

        assertEquals(utilisateurB, existant.getUser());
        assertTrue(existant.isActive());
        assertEquals(0, existant.getFailureCount());
        assertEquals("cle-p256dh", existant.getP256dh());
    }

    @Test
    void suppression_uniquementSiLAbonnementAppartientALUtilisateurConnecte() {
        WebPushSubscription deA = WebPushSubscription.builder().id(5L).user(utilisateurA).endpoint(ENDPOINT).active(true).build();
        when(repository.findByEndpointHash(WebPushSubscriptionService.hash(ENDPOINT))).thenReturn(Optional.of(deA));

        connecte(utilisateurB);
        service.supprimer(ENDPOINT);
        assertTrue(deA.isActive());
        verify(repository, never()).save(any());

        connecte(utilisateurA);
        service.supprimer(ENDPOINT);
        assertFalse(deA.isActive());
    }

    @Test
    void suppressionEndpointInconnu_sansErreur() {
        connecte(utilisateurA);
        when(repository.findByEndpointHash(any())).thenReturn(Optional.empty());

        service.supprimer("https://inconnu.example/x");

        verify(repository, never()).save(any());
    }
}
