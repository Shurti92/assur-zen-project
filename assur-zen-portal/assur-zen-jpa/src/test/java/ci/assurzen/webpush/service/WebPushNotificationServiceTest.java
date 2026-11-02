package ci.assurzen.webpush.service;

import ci.assurzen.entities.User;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.webpush.VapidTestKeys;
import ci.assurzen.webpush.config.WebPushProperties;
import ci.assurzen.webpush.dto.WebPushMessage;
import ci.assurzen.webpush.entity.WebPushSubscription;
import ci.assurzen.webpush.repository.WebPushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Envoi des notifications Web Push (2026-09-26) : ciblage utilisateur/plusieurs/role/partenaire, tous
 * les appareils, desactivation des abonnements invalides, interrupteur WEBPUSH_ENABLED. */
class WebPushNotificationServiceTest {

    private final WebPushSender sender = mock(WebPushSender.class);
    private final WebPushSubscriptionRepository repository = mock(WebPushSubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final DynamicConfigurationService dynamicConfiguration = mock(DynamicConfigurationService.class);
    private final WebPushProperties properties = VapidTestKeys.proprietesConfigurees();

    private final WebPushNotificationService service = new WebPushNotificationService(
            properties, sender, repository, userRepository, partenaireRepository, dynamicConfiguration);

    private final WebPushMessage message = new WebPushMessage("Nouvelle commission disponible", "corps",
            Map.of("type", "COMMISSION_GAGNEE", "reference", "42", "route", "/commissions"));

    @BeforeEach
    void setUp() {
        when(dynamicConfiguration.getBoolean(eq("WEBPUSH_ENABLED"), any())).thenReturn(true);
    }

    private static User utilisateur(long id, String roles) {
        User user = new User();
        user.setId(id);
        user.setRoles(roles);
        return user;
    }

    private static WebPushSubscription abonnement(long id, long userId) {
        return WebPushSubscription.builder().id(id).user(utilisateur(userId, "PARTENAIRE"))
                .endpoint("https://push.example/" + id).p256dh("p").auth("a").build();
    }

    @Test
    void sendToUser_tousLesAppareilsDeLUtilisateur() {
        WebPushSubscription telephone = abonnement(1, 70);
        WebPushSubscription ordinateur = abonnement(2, 70);
        when(repository.findByUserIdAndActiveTrue(70L)).thenReturn(List.of(telephone, ordinateur));
        when(sender.envoyer(any(), anyString())).thenReturn(new WebPushSender.Envoi(WebPushSender.Resultat.ENVOYE, "HTTP 201"));

        assertEquals(2, service.sendToUser(70L, message));
        assertNotNull(telephone.getLastUsedAt());
        assertNotNull(ordinateur.getLastUsedAt());
    }

    @Test
    void abonnementExpire_estDesactive_etNeCompteDansLesAppareilsAtteints() {
        WebPushSubscription expire = abonnement(1, 70);
        when(repository.findByUserIdAndActiveTrue(70L)).thenReturn(List.of(expire));
        when(sender.envoyer(any(), anyString())).thenReturn(new WebPushSender.Envoi(WebPushSender.Resultat.INVALIDE, "HTTP 410"));

        assertEquals(0, service.sendToUser(70L, message));
        assertFalse(expire.isActive());
        verify(repository).save(expire);
    }

    @Test
    void echecsTransitoires_repetes_desactiventLAbonnementAuSeuil() {
        WebPushSubscription abonnement = abonnement(1, 70);
        abonnement.setFailureCount(properties.getMaxFailures() - 1);
        when(repository.findByUserIdAndActiveTrue(70L)).thenReturn(List.of(abonnement));
        when(sender.envoyer(any(), anyString())).thenReturn(new WebPushSender.Envoi(WebPushSender.Resultat.ECHEC, "HTTP 503"));

        assertEquals(0, service.sendToUser(70L, message));
        assertFalse(abonnement.isActive());
    }

    @Test
    void sendToUsers_etSendToRole() {
        when(repository.findByUserIdInAndActiveTrue(any())).thenReturn(List.of(abonnement(1, 70), abonnement(2, 71)));
        when(sender.envoyer(any(), anyString())).thenReturn(new WebPushSender.Envoi(WebPushSender.Resultat.ENVOYE, "HTTP 201"));
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(1, "ADMIN"), utilisateur(70, "COMMERCIAL"),
                utilisateur(71, "ADMIN,COMMERCIAL")));

        assertEquals(2, service.sendToUsers(List.of(70L, 71L), message));
        assertEquals(2, service.sendToRole("commercial", message));
        verify(repository, times(2)).findByUserIdInAndActiveTrue(List.of(70L, 71L));
    }

    @Test
    void sendToPartner_viseLeCompteDeConnexionDuPartenaire() {
        when(partenaireRepository.findUserIdById(7L)).thenReturn(Optional.of(70L));
        when(repository.findByUserIdAndActiveTrue(70L)).thenReturn(List.of(abonnement(1, 70)));
        when(sender.envoyer(any(), anyString())).thenReturn(new WebPushSender.Envoi(WebPushSender.Resultat.ENVOYE, "HTTP 201"));

        assertEquals(1, service.sendToPartner(7L, message));
    }

    @Test
    void pushCoupeParWEBPUSH_ENABLED_aucunEnvoi() {
        when(dynamicConfiguration.getBoolean(eq("WEBPUSH_ENABLED"), any())).thenReturn(false);
        when(repository.existsByUserIdAndActiveTrue(70L)).thenReturn(true);

        assertEquals(0, service.sendToUser(70L, message));
        assertFalse(service.peutRecevoir(70L));
        verify(sender, never()).envoyer(any(), anyString());
    }

    @Test
    void clesVapidAbsentes_pushInactif() {
        WebPushNotificationService sansCles = new WebPushNotificationService(new WebPushProperties(), sender, repository,
                userRepository, partenaireRepository, dynamicConfiguration);
        when(repository.existsByUserIdAndActiveTrue(70L)).thenReturn(true);

        assertFalse(sansCles.estActif());
        assertFalse(sansCles.peutRecevoir(70L));
        assertTrue(service.peutRecevoir(70L));
    }
}
