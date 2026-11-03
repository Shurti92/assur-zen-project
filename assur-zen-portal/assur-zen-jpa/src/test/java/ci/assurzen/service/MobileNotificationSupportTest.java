package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.MobileNotification;
import ci.assurzen.entities.SupportRequest;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.exception.MobilePaymentException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MobileFaqRepository;
import ci.assurzen.repositories.MobileNotificationRepository;
import ci.assurzen.repositories.SupportRequestRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CommercialService;
import ci.assurzen.services.DynamicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications in-app et support du mobile (2026-11-03). */
class MobileNotificationSupportTest {

    private MobileNotificationRepository notifRepo;
    private ClientAccountInsuredRepository liens;
    private DynamicConfigurationService config;
    private MobileNotificationService notifications;

    private SupportRequestRepository supportRepo;
    private MobileSupportService support;

    @BeforeEach
    void setUp() {
        notifRepo = mock(MobileNotificationRepository.class);
        liens = mock(ClientAccountInsuredRepository.class);
        config = mock(DynamicConfigurationService.class);
        notifications = new MobileNotificationService(notifRepo, liens, mock(ContractRepository.class), config, mock(FcmPushService.class));
        when(notifRepo.save(any(MobileNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(config.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getString(eq("SUPPORT_TOPICS"), any())).thenReturn("CONTRACT:Contrat;PAYMENT:Paiement;OTHER:Autre");

        supportRepo = mock(SupportRequestRepository.class);
        ci.assurzen.service.AuthService auth = mock(ci.assurzen.service.AuthService.class);
        when(auth.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(29L, "admin", "ADMIN"));
        support = new MobileSupportService(supportRepo, mock(MobileFaqRepository.class), mock(UserRepository.class), config, notifications,
                mock(CommercialService.class), auth);
        when(supportRepo.saveAndFlush(any(SupportRequest.class))).thenAnswer(inv -> {
            SupportRequest r = inv.getArgument(0);
            r.setId(4L);
            return r;
        });
        when(supportRepo.save(any(SupportRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void notifier_creeLaNotificationEtIgnoreUnDoublon() {
        notifications.notifier(45L, "CLAIM", "Sinistre S1", "Nouveau statut", null, 12L, "K1");
        verify(notifRepo).save(any(MobileNotification.class));

        when(notifRepo.existsByUserIdAndDedupeKey(45L, "K2")).thenReturn(true);
        notifications.notifier(45L, "EXPIRY", "x", "y", 1L, null, "K2");
        verify(notifRepo, org.mockito.Mockito.times(1)).save(any(MobileNotification.class));
    }

    @Test
    void notifierAssure_atteintTousLesComptesRattaches() {
        when(liens.findByInsuredId(627L)).thenReturn(List.of(new ClientAccountInsured(45L, 627L), new ClientAccountInsured(46L, 627L)));
        notifications.notifierAssure(627L, "SERVICE", "t", "b", null, null, null);
        verify(notifRepo, org.mockito.Mockito.times(2)).save(any(MobileNotification.class));
    }

    @Test
    void liste_renvoieLesNonLuesEtMarquerLueEstIdempotent() {
        MobileNotification n = new MobileNotification();
        n.setId(9L);
        n.setUserId(45L);
        n.setCategory("PAYMENT");
        n.setTitle("t");
        n.setBody("b");
        when(notifRepo.findByUserIdOrderByCreatedAtDesc(eq(45L), any())).thenReturn(new PageImpl<>(List.of(n)));
        when(notifRepo.countByUserIdAndReadAtIsNull(45L)).thenReturn(1L);
        MobileExtraDtos.NotificationPage page = notifications.lister(45L, 0, 20);
        assertEquals(1L, page.unreadCount());
        assertEquals(false, page.items().get(0).read());

        when(notifRepo.findByIdAndUserId(9L, 45L)).thenReturn(Optional.of(n));
        assertTrue(notifications.marquerLue(45L, 9L).read());
        assertTrue(notifications.marquerLue(45L, 9L).read());
        verify(notifRepo, org.mockito.Mockito.times(1)).save(n);

        when(notifRepo.findByIdAndUserId(99L, 45L)).thenReturn(Optional.empty());
        assertEquals("NOTIFICATION_NOT_FOUND", assertThrows(MobileApiException.class, () -> notifications.marquerLue(45L, 99L)).getCode());
    }

    @Test
    void sujets_sontAnalysesDepuisLaConfiguration() {
        assertEquals(List.of("CONTRACT", "PAYMENT", "OTHER"), support.sujets().items().stream().map(MobileExtraDtos.Topic::id).toList());
        assertTrue(MobileSupportService.analyserSujets("").isEmpty());
        assertEquals("Sinistre", MobileSupportService.analyserSujets("claim:Sinistre; ;mauvais").get(0).label());
    }

    @Test
    void demande_creeUneReferenceEtAnnonceLeDelaiSeulementSiConfigure() {
        MobileExtraDtos.SupportCreated cree = support.creer(45L, new MobileExtraDtos.SupportCreateRequest("contract", "Mon contrat pose probleme"));
        assertTrue(cree.reference().startsWith("SP-"));
        assertNull(cree.expectedWithinMinutes());

        when(config.getLong(eq("SUPPORT_RESPONSE_MINUTES"), anyLong())).thenReturn(120L);
        assertEquals(120, support.creer(45L, new MobileExtraDtos.SupportCreateRequest("OTHER", "Une autre question")).expectedWithinMinutes());
    }

    @Test
    void demande_sujetInconnuEstRefuseEtLeSixiemeMessageDeLHeureDonne429() {
        assertEquals("topic", assertThrows(MobileApiException.class,
                () -> support.creer(45L, new MobileExtraDtos.SupportCreateRequest("INCONNU", "Message valide"))).getField());

        SupportRequest ancienne = new SupportRequest();
        ancienne.setCreatedAt(java.time.Instant.now());
        when(supportRepo.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(eq(45L), any()))
                .thenReturn(List.of(ancienne, ancienne, ancienne, ancienne, ancienne));
        MobilePaymentException e = assertThrows(MobilePaymentException.class,
                () -> support.creer(45L, new MobileExtraDtos.SupportCreateRequest("OTHER", "Encore un message")));
        assertEquals("RATE_LIMITED", e.getCode());
    }

    @Test
    void reponse_exigeUnTexteEtNotifieLeClient() {
        SupportRequest demande = new SupportRequest();
        demande.setId(4L);
        demande.setUserId(45L);
        demande.setReference("SP-2026-000004");
        demande.setTopic("OTHER");
        demande.setMessage("Question");
        when(supportRepo.findById(4L)).thenReturn(Optional.of(demande));

        assertEquals("response", assertThrows(MobileApiException.class,
                () -> support.traiter(4L, new MobileExtraDtos.SupportProcessRequest("REPONDU", " "))).getField());
        verify(notifRepo, never()).save(any(MobileNotification.class));

        MobileExtraDtos.SupportAdminItem item = support.traiter(4L, new MobileExtraDtos.SupportProcessRequest("REPONDU", "Voici la reponse"));
        assertEquals("REPONDU", item.status());
        assertEquals("admin", item.respondedBy());
        verify(notifRepo).save(any(MobileNotification.class));
    }
}
