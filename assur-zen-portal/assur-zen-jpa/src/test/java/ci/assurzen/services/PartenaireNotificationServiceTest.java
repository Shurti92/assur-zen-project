package ci.assurzen.services;

import ci.assurzen.dto.PartenaireNotificationResponse;
import ci.assurzen.events.NotificationUtilisateurEvent;
import ci.assurzen.events.PartenaireNotificationCreeeEvent;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireNotification;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.PushStatus;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.PartenaireNotificationRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.webpush.dto.WebPushMessage;
import ci.assurzen.webpush.service.WebPushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications de l'espace partenaire (2026-09-23). */
class PartenaireNotificationServiceTest {

    private final PartenaireNotificationRepository notificationRepository = mock(PartenaireNotificationRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
    private final AuthService authService = mock(AuthService.class);
    private final WebPushNotificationService webPushNotificationService = mock(WebPushNotificationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationPreferenceService preferenceService = preferencesParDefaut();
    private final PartenaireNotificationService service = new PartenaireNotificationService(
            notificationRepository, partenaireRepository, profileRepository, smsNotificationService, authService,
            webPushNotificationService, eventPublisher, preferenceService);

    /** Sans preference enregistree, tout est autorise (comportement historique). */
    private static NotificationPreferenceService preferencesParDefaut() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.pushAutorise(any(), any())).thenReturn(true);
        when(preferences.smsAutorise(any(), any())).thenReturn(true);
        return preferences;
    }

    @Test
    void notifier_echecSms_creeQuandMemeLaNotificationInApp() {
        Partenaire partenaire = Partenaire.builder().id(7L).nom("Koffi").telephone("0700000007").build();
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(partenaire));
        when(smsNotificationService.sendFromTemplate(any(), any(), any(), any())).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_DISPONIBLE,
                "Commissions disponibles", "Vous avez 25 000 FCFA...", 25000L, null,
                "COMMISSION_DISPONIBLE", Map.of("montant", "25 000"), TypeMessageSms.commission_disponible);

        assertFalse(notification.isSmsEnvoye());
        assertFalse(notification.isLu());
        assertEquals(partenaire, notification.getPartenaire());
    }

    @Test
    void acces_partenaireConnecte_autorise_autrePartenaire_refuse403() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(50L, "0700000007", "PARTENAIRE"));
        when(profileRepository.findByUserId(50L)).thenReturn(Optional.of(Profile.builder().id(500L).build()));
        when(partenaireRepository.findByProfile_Id(500L)).thenReturn(List.of(Partenaire.builder().id(7L).build()));
        when(notificationRepository.countByPartenaireIdAndLuFalse(7L)).thenReturn(3L);

        assertEquals(3L, service.compterNonLues(7L));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.compterNonLues(8L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void marquerLue_notificationDUnAutrePartenaire_renvoie404() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(notificationRepository.findByIdAndPartenaireId(99L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.marquerLue(7L, 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void marquerLue_renseigneLaDateDeLecture() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        PartenaireNotification notification = PartenaireNotification.builder().id(5L)
                .type(PartenaireNotificationType.COMMISSION_VERSEE).titre("t").message("m").build();
        when(notificationRepository.findByIdAndPartenaireId(5L, 7L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.marquerLue(7L, 5L).lu());
        assertNotNull(notification.getLuAt());
    }

    // ---- Web Push de l'application EP (2026-09-26) ----

    private Partenaire partenaireAbonne(boolean abonne) {
        Partenaire partenaire = Partenaire.builder().id(7L).nom("Koffi").telephone("0700000007").build();
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(partenaire));
        when(webPushNotificationService.userIdDuPartenaire(7L)).thenReturn(Optional.of(70L));
        when(webPushNotificationService.peutRecevoir(70L)).thenReturn(abonne);
        when(smsNotificationService.sendFromTemplate(any(), any(), any(), any())).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            PartenaireNotification n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(42L);
            }
            return n;
        });
        return partenaire;
    }

    @Test
    void commissionDisponible_partenaireAbonne_pushAuLieuDuSmsAvecSmsDeSecours() {
        partenaireAbonne(true);

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_GAGNEE,
                "Nouvelle commission disponible", "Une nouvelle commission de 7 000 FCFA...", 7000L, 49L,
                "COMMISSION_GAGNEE", Map.of("montant", "7 000"), TypeMessageSms.commission_gagnee);

        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
        assertEquals(PushStatus.PENDING, notification.getPushStatus());
        ArgumentCaptor<PartenaireNotificationCreeeEvent> event = ArgumentCaptor.forClass(PartenaireNotificationCreeeEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(42L, event.getValue().notificationId());
        assertEquals(70L, event.getValue().userId());
        assertTrue(event.getValue().smsDeSecours());
    }

    @Test
    void commissionVersee_partenaireAbonne_smsToujoursEnvoyeEtPushEnPlus() {
        partenaireAbonne(true);

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_VERSEE,
                "Commission versee", "Votre commission de 5 000 FCFA...", 5000L, 9L,
                "COMMISSION_PAYEE", Map.of("montant", "5 000"), TypeMessageSms.commission_payee);

        assertTrue(notification.isSmsEnvoye());
        ArgumentCaptor<PartenaireNotificationCreeeEvent> event = ArgumentCaptor.forClass(PartenaireNotificationCreeeEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertFalse(event.getValue().smsDeSecours());
    }

    @Test
    void partenaireSansAppareilAbonne_smsCommeAvantEtAucunPush() {
        partenaireAbonne(false);

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_DISPONIBLE,
                "Commissions disponibles", "Vous avez 25 000 FCFA...", 25000L, null,
                "COMMISSION_DISPONIBLE", Map.of("montant", "25 000"), TypeMessageSms.commission_disponible);

        assertTrue(notification.isSmsEnvoye());
        assertEquals(PushStatus.NONE, notification.getPushStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private PartenaireNotification notificationEnregistree(PartenaireNotificationType type) {
        PartenaireNotification notification = PartenaireNotification.builder().id(42L).type(type)
                .titre("Nouvelle commission disponible").message("Une nouvelle commission de 7 000 FCFA").montant(7000L)
                .pushStatus(PushStatus.PENDING).build();
        when(notificationRepository.findById(42L)).thenReturn(Optional.of(notification));
        return notification;
    }

    private static PartenaireNotificationCreeeEvent evenement(boolean smsDeSecours) {
        return new PartenaireNotificationCreeeEvent(42L, 70L, smsDeSecours, "0700000007", "COMMISSION_GAGNEE",
                Map.of("montant", "7 000"), TypeMessageSms.commission_gagnee);
    }

    @Test
    void envoyerPush_appareilAtteint_statutSentSansSms() {
        PartenaireNotification notification = notificationEnregistree(PartenaireNotificationType.COMMISSION_GAGNEE);
        when(webPushNotificationService.sendToUser(eq(70L), any())).thenReturn(1);

        service.envoyerPush(evenement(true));

        assertEquals(PushStatus.SENT, notification.getPushStatus());
        assertNotNull(notification.getPushSentAt());
        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
    }

    @Test
    void envoyerPush_aucunAppareilAtteint_smsDeSecours() {
        PartenaireNotification notification = notificationEnregistree(PartenaireNotificationType.COMMISSION_GAGNEE);
        when(webPushNotificationService.sendToUser(eq(70L), any())).thenReturn(0);
        when(smsNotificationService.sendFromTemplate(eq("0700000007"), eq("COMMISSION_GAGNEE"), any(), any())).thenReturn(true);

        service.envoyerPush(evenement(true));

        assertEquals(PushStatus.FAILED, notification.getPushStatus());
        assertTrue(notification.isSmsEnvoye());
    }

    @Test
    void messagePush_sansMontantNiDonneePersonnelle_referenceEtRouteDeLApplication() {
        PartenaireNotification notification = PartenaireNotification.builder().id(42L)
                .type(PartenaireNotificationType.COMMISSION_GAGNEE).titre("t")
                .message("Une nouvelle commission de 7 000 FCFA (contrat CTR2026000246)").montant(7000L).build();

        WebPushMessage message = PartenaireNotificationService.messagePush(notification);

        assertFalse((message.title() + message.body()).matches(".*\\d.*"));
        assertEquals(Map.of("type", "COMMISSION_GAGNEE", "reference", "42", "route", "/commissions"), message.data());
    }

    // ---- Centre de notifications etendu au commercial interne, suppression (2026-10-03) ----

    @Test
    void mesNotifications_compteNonPartenaire_cibleSonCompteUtilisateur() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "commercial", "COMMERCIAL"));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        PartenaireNotification bonus = PartenaireNotification.builder().id(3L).userId(1L)
                .type(PartenaireNotificationType.BONUS_PAYE).titre("Bonus paye").message("m")
                .objetType("PERFORMANCE_COMMERCIALE").objetId(12L).build();
        when(notificationRepository.findVisibles(eq(null), eq(1L), eq(false), any())).thenReturn(new PageImpl<>(List.of(bonus)));
        when(notificationRepository.countVisiblesNonLues(null, 1L)).thenReturn(1L);

        PartenaireNotificationResponse reponse = service.listerMesNotifications(false, 0, 20).content().get(0);

        assertEquals("PERFORMANCE_COMMERCIALE", reponse.objetType());
        assertEquals(12L, reponse.objetId());
        assertEquals("/performance", reponse.route());
        assertEquals(1L, service.compterMesNonLues());
    }

    @Test
    void mesNotifications_partenaire_ancienneNotificationDeCommission_objetContratDeduit() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(50L, "0700000007", "PARTENAIRE"));
        when(profileRepository.findByUserId(50L)).thenReturn(Optional.of(Profile.builder().id(500L).build()));
        Partenaire partenaire = Partenaire.builder().id(7L).build();
        when(partenaireRepository.findByProfile_Id(500L)).thenReturn(List.of(partenaire));
        PartenaireNotification ancienne = PartenaireNotification.builder().id(4L).partenaire(partenaire)
                .type(PartenaireNotificationType.COMMISSION_GAGNEE).titre("t").message("m").contractId(49L).build();
        when(notificationRepository.findVisibles(eq(7L), eq(null), eq(true), any())).thenReturn(new PageImpl<>(List.of(ancienne)));

        PartenaireNotificationResponse reponse = service.listerMesNotifications(true, 0, 20).content().get(0);

        assertEquals("CONTRAT", reponse.objetType());
        assertEquals(49L, reponse.objetId());
        assertEquals("/commissions", reponse.route());
    }

    @Test
    void supprimerMaNotification_masqueeEtMarqueeLue() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "commercial", "COMMERCIAL"));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        PartenaireNotification notification = PartenaireNotification.builder().id(3L).userId(1L)
                .type(PartenaireNotificationType.BONUS_VALIDE).titre("t").message("m").build();
        when(notificationRepository.findVisible(3L, null, 1L)).thenReturn(Optional.of(notification));

        service.supprimerMaNotification(3L);

        assertTrue(notification.getSupprimee());
        assertNotNull(notification.getSupprimeeAt());
        assertTrue(notification.isLu());
        verify(notificationRepository).save(notification);
    }

    @Test
    void supprimerMaNotification_pasLaMienne_404() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "commercial", "COMMERCIAL"));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(notificationRepository.findVisible(9L, null, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.supprimerMaNotification(9L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void notifierInApp_commercialAbonne_notificationSansSmsEtPush() {
        when(webPushNotificationService.peutRecevoir(1L)).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            PartenaireNotification n = inv.getArgument(0);
            n.setId(43L);
            return n;
        });

        PartenaireNotification notification = service.notifierInApp(NotificationUtilisateurEvent.pourUtilisateur(1L,
                PartenaireNotificationType.COMMISSION_COMMERCIALE_PAYEE, "Commission payee", "m", 5000L, "CONTRAT", 9L));

        assertNull(notification.getPartenaire());
        assertEquals(1L, notification.getUserId());
        assertEquals(9L, notification.getContractId());
        assertFalse(notification.isSmsEnvoye());
        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
        ArgumentCaptor<PartenaireNotificationCreeeEvent> event = ArgumentCaptor.forClass(PartenaireNotificationCreeeEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(1L, event.getValue().userId());
        assertFalse(event.getValue().smsDeSecours());
    }

    @Test
    void notifierInApp_demandeDuPartenaire_rattacheeAuPartenaire() {
        partenaireAbonne(false);

        PartenaireNotification notification = service.notifierInApp(NotificationUtilisateurEvent.pourPartenaire(7L,
                PartenaireNotificationType.DEMANDE_PAIEMENT_PAYEE, "Demande de paiement payee", "m", 10800L, "DEMANDE_PAIEMENT", 5L));

        assertEquals(7L, notification.getPartenaire().getId());
        assertNull(notification.getUserId());
        assertEquals("/commissions", PartenaireNotificationService.route(notification));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- Preferences de notification (2026-10-03) ----

    @Test
    void commissionGagnee_smsCoupeParLUtilisateur_niSmsNiSmsDeSecours() {
        partenaireAbonne(true);
        when(preferenceService.smsAutorise(70L, PartenaireNotificationType.COMMISSION_GAGNEE)).thenReturn(false);

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_GAGNEE,
                "Nouvelle commission disponible", "m", 7000L, 49L,
                "COMMISSION_GAGNEE", Map.of("montant", "7 000"), TypeMessageSms.commission_gagnee);

        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
        assertEquals(PushStatus.PENDING, notification.getPushStatus());
        ArgumentCaptor<PartenaireNotificationCreeeEvent> event = ArgumentCaptor.forClass(PartenaireNotificationCreeeEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertFalse(event.getValue().smsDeSecours());
    }

    @Test
    void commissionGagnee_pushCoupeParLUtilisateur_smsCommeSansAppareil() {
        partenaireAbonne(true);
        when(preferenceService.pushAutorise(70L, PartenaireNotificationType.COMMISSION_GAGNEE)).thenReturn(false);

        PartenaireNotification notification = service.notifier(7L, PartenaireNotificationType.COMMISSION_GAGNEE,
                "Nouvelle commission disponible", "m", 7000L, 49L,
                "COMMISSION_GAGNEE", Map.of("montant", "7 000"), TypeMessageSms.commission_gagnee);

        assertTrue(notification.isSmsEnvoye());
        assertEquals(PushStatus.NONE, notification.getPushStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- Page cible du push pour tous les types (2026-10-05) ----

    @Test
    void route_etDonneesDuPush_pourLesNouveauxTypes() {
        PartenaireNotification echeance = PartenaireNotification.builder().id(50L).partenaire(Partenaire.builder().id(7L).build())
                .type(PartenaireNotificationType.ECHEANCE_PROCHE).titre("t").message("m").objetType("CONTRAT").objetId(468L).build();
        assertEquals(Map.of("type", "ECHEANCE_PROCHE", "reference", "50", "route", "/contracts/468",
                "objetType", "CONTRAT", "objetId", "468"), PartenaireNotificationService.messagePush(echeance).data());

        assertEquals("/documents", PartenaireNotificationService.route(PartenaireNotification.builder()
                .type(PartenaireNotificationType.DOCUMENT_REFUSE).objetType("DOCUMENT").objetId(3L).build()));
        assertEquals("/profile", PartenaireNotificationService.route(PartenaireNotification.builder()
                .type(PartenaireNotificationType.NOUVELLE_CONNEXION).objetType("CONNEXION").objetId(9L).build()));
        assertEquals("/contracts", PartenaireNotificationService.route(PartenaireNotification.builder()
                .type(PartenaireNotificationType.PRIME_NON_REVERSEE).build()));
        assertEquals("/commissions", PartenaireNotificationService.route(PartenaireNotification.builder()
                .type(PartenaireNotificationType.DEMANDE_PAIEMENT_PAYEE).userId(1L).build()));
    }
}
