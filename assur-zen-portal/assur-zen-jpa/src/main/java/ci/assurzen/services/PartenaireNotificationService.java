package ci.assurzen.services;

import ci.assurzen.dto.PageResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Notifications de l'espace partenaire (2026-09-23) : chaque notification est envoyee par SMS ET
 * conservee in-app (historique consultable par le partenaire et les admins). L'echec du SMS ne bloque
 * jamais la creation de la notification in-app (smsEnvoye = false trace l'echec).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartenaireNotificationService {

    private final PartenaireNotificationRepository notificationRepository;
    private final PartenaireRepository partenaireRepository;
    private final ProfileRepository profileRepository;
    private final SmsNotificationService smsNotificationService;
    private final AuthService authService;
    private final WebPushNotificationService webPushNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationPreferenceService preferenceService;

    /** SMS (modele de communication smsTemplateCode) + notification in-app. Jamais d'exception sur un
     * echec SMS : sendFromTemplate renvoie simplement false. Prend un id (et relit le partenaire dans
     * sa propre transaction) : appele depuis des traitements asynchrones/planifies sans session JPA. */
    @Transactional
    public PartenaireNotification notifier(Long partenaireId, PartenaireNotificationType type, String titre, String message,
                                           Long montant, Long contractId, String smsTemplateCode,
                                           Map<String, String> smsVariables, TypeMessageSms typeSms) {
        Partenaire partenaire = partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable: " + partenaireId));
        // Push navigateur de l'application EP (2026-09-26) : si le partenaire a un appareil abonne, le push
        // REMPLACE le SMS pour les commissions disponibles (SMS seulement en secours, si aucun appareil
        // n'est atteint). COMMISSION_VERSEE garde toujours son SMS : confirmation d'un paiement, suivie
        // par CommissionPayment.notificationEnvoyee.
        Long userId = webPushNotificationService.userIdDuPartenaire(partenaireId).orElse(null);
        // Preferences de l'utilisateur (2026-10-03) : push et SMS coupables par categorie, sauf le SMS de
        // l'argent verse (COMMISSION_VERSEE) qui reste toujours envoye.
        boolean pushPossible = webPushNotificationService.peutRecevoir(userId) && preferenceService.pushAutorise(userId, type);
        boolean smsAutorise = preferenceService.smsAutorise(userId, type);
        boolean smsImmediat = smsAutorise && (!pushPossible || type == PartenaireNotificationType.COMMISSION_VERSEE);

        boolean smsEnvoye = false;
        if (smsImmediat) {
            smsEnvoye = smsNotificationService.sendFromTemplate(partenaire.getTelephone(), smsTemplateCode, smsVariables, typeSms);
            if (!smsEnvoye) {
                log.warn("SMS {} non envoye au partenaire {} - notification in-app creee quand meme", smsTemplateCode, partenaire.getId());
            }
        }
        PartenaireNotification saved = notificationRepository.save(PartenaireNotification.builder()
                .partenaire(partenaire)
                .type(type)
                .titre(titre)
                .message(message)
                .montant(montant)
                .contractId(contractId)
                .smsEnvoye(smsEnvoye)
                .pushStatus(pushPossible ? PushStatus.PENDING : PushStatus.NONE)
                .build());
        if (pushPossible) {
            eventPublisher.publishEvent(new PartenaireNotificationCreeeEvent(saved.getId(), userId, smsAutorise && !smsImmediat,
                    partenaire.getTelephone(), smsTemplateCode, smsVariables, typeSms));
        }
        return saved;
    }

    /**
     * Notification in-app (+ push si un appareil est abonne, jamais de SMS) demandee par
     * NotificationUtilisateurEvent - 2026-10-03. REQUIRES_NEW : appelee apres commit de l'operation
     * metier (NotificationUtilisateurListener), une transaction neuve est necessaire pour persister.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartenaireNotification notifierInApp(NotificationUtilisateurEvent event) {
        Partenaire partenaire = event.partenaireId() == null ? null : partenaireRepository.findById(event.partenaireId())
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable: " + event.partenaireId()));
        if (partenaire == null && event.userId() == null) {
            throw new IllegalArgumentException("Notification sans destinataire");
        }
        Long userIdPush = partenaire != null
                ? webPushNotificationService.userIdDuPartenaire(partenaire.getId()).orElse(null)
                : event.userId();
        boolean pushPossible = webPushNotificationService.peutRecevoir(userIdPush)
                && preferenceService.pushAutorise(userIdPush, event.type());
        PartenaireNotification saved = notificationRepository.save(PartenaireNotification.builder()
                .partenaire(partenaire)
                .userId(partenaire == null ? event.userId() : null)
                .type(event.type())
                .titre(tronquer(event.titre(), 150))
                .message(tronquer(event.message(), 500))
                .montant(event.montant())
                .contractId("CONTRAT".equals(event.objetType()) ? event.objetId() : null)
                .objetType(event.objetType())
                .objetId(event.objetId())
                .smsEnvoye(false)
                .pushStatus(pushPossible ? PushStatus.PENDING : PushStatus.NONE)
                .build());
        if (pushPossible) {
            eventPublisher.publishEvent(new PartenaireNotificationCreeeEvent(saved.getId(), userIdPush, false,
                    null, null, null, null));
        }
        return saved;
    }

    private static String tronquer(String valeur, int max) {
        return valeur == null || valeur.length() <= max ? valeur : valeur.substring(0, max);
    }

    /**
     * Envoi push d'une notification deja enregistree (apres commit, asynchrone - cf.
     * PartenaireNotificationPushListener). Aucun montant ni donnee personnelle dans le push (decision du
     * 2026-09-26, contrat notifications-push.md de l'equipe EP) : le detail reste dans l'application.
     * SMS de secours si aucun appareil n'est atteint.
     */
    @Transactional
    public void envoyerPush(PartenaireNotificationCreeeEvent event) {
        PartenaireNotification notification = notificationRepository.findById(event.notificationId()).orElse(null);
        if (notification == null) {
            return;
        }
        int atteints = webPushNotificationService.sendToUser(event.userId(), messagePush(notification));
        if (atteints > 0) {
            notification.setPushStatus(PushStatus.SENT);
            notification.setPushSentAt(Instant.now());
            notification.setPushError(null);
        } else {
            notification.setPushStatus(PushStatus.FAILED);
            notification.setPushError("aucun appareil atteint");
            if (event.smsDeSecours()) {
                boolean smsEnvoye = smsNotificationService.sendFromTemplate(event.telephone(), event.smsTemplateCode(),
                        event.smsVariables(), event.typeSms());
                notification.setSmsEnvoye(smsEnvoye);
                log.info("Push non recu pour la notification {} - SMS de secours {}", notification.getId(),
                        smsEnvoye ? "envoye" : "en echec");
            }
        }
        notificationRepository.save(notification);
    }

    /** data.type + data.reference (id de la notification) servent d'anti-doublon cote service worker ;
     * data.route est une route interne de l'application EP. */
    static WebPushMessage messagePush(PartenaireNotification notification) {
        String titre;
        String corps;
        switch (notification.getType()) {
            case COMMISSION_GAGNEE -> {
                titre = "Nouvelle commission disponible";
                corps = "Une nouvelle commission est disponible dans votre espace partenaire.";
            }
            case COMMISSION_DISPONIBLE -> {
                titre = "Commissions disponibles";
                corps = "Vous avez des commissions disponibles. Vous pouvez en demander le paiement depuis votre espace.";
            }
            case COMMISSION_VERSEE -> {
                titre = "Commission versée";
                corps = "Votre commission a été versée sur votre compte Wave.";
            }
            case DEMANDE_PAIEMENT_PAYEE -> {
                titre = "Demande de paiement payée";
                corps = "Votre demande de paiement a été payée.";
            }
            case DEMANDE_PAIEMENT_ECHEC -> {
                titre = "Demande de paiement";
                corps = "Le virement de votre demande de paiement n'a pas abouti. Il sera relancé.";
            }
            case BONUS_VALIDE -> {
                titre = "Bonus validé";
                corps = "Votre bonus du mois a été validé.";
            }
            case BONUS_PAYE -> {
                titre = "Bonus payé";
                corps = "Votre bonus du mois a été payé.";
            }
            case ECHEANCE_PROCHE -> {
                titre = "Échéance proche";
                corps = "Un de vos contrats arrive bientôt à échéance.";
            }
            case PRIME_NON_REVERSEE -> {
                titre = "Prime à reverser";
                corps = "Une prime encaissée n'a pas encore été reversée à la compagnie.";
            }
            case DOCUMENT_EXPIRE_BIENTOT -> {
                titre = "Document bientôt expiré";
                corps = "Un de vos documents arrive bientôt à expiration.";
            }
            case DOCUMENT_EXPIRE -> {
                titre = "Document expiré";
                corps = "Un de vos documents a expiré. Merci d'en fournir un nouveau.";
            }
            case DOCUMENT_REFUSE -> {
                titre = "Document refusé";
                corps = "Un de vos documents a été refusé. Consultez le motif dans votre espace.";
            }
            case NOUVELLE_CONNEXION -> {
                titre = "Nouvelle connexion";
                corps = "Une connexion à votre compte depuis un nouvel appareil a eu lieu.";
            }
            case COMMISSION_COMMERCIALE_PAYEE -> {
                titre = "Commission payée";
                corps = "Vos commissions ont été payées.";
            }
            default -> {
                titre = notification.getTitre();
                corps = "Vous avez une nouvelle notification.";
            }
        }
        // data : type + reference (anti-doublon du service worker), route (page a ouvrir, toujours renseignee),
        // et objetType/objetId quand la notification porte sur un objet precis (2026-10-05).
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("type", notification.getType().name());
        data.put("reference", String.valueOf(notification.getId()));
        data.put("route", route(notification) != null ? route(notification) : "/notifications");
        if (objetType(notification) != null && objetId(notification) != null) {
            data.put("objetType", objetType(notification));
            data.put("objetId", String.valueOf(objetId(notification)));
        }
        return new WebPushMessage(titre, corps, data);
    }

    /** Type d'objet a ouvrir : celui enregistre, sinon CONTRAT pour les anciennes notifications de
     * commission (contractId). */
    static String objetType(PartenaireNotification n) {
        if (n.getObjetType() != null) {
            return n.getObjetType();
        }
        return n.getContractId() != null ? "CONTRAT" : null;
    }

    static Long objetId(PartenaireNotification n) {
        if (n.getObjetType() != null) {
            return n.getObjetId();
        }
        return n.getContractId();
    }

    /** Page de l'application EP a ouvrir (liste blanche du service worker EP : /, /a-traiter, /contracts,
     * /contracts/{id}, /commissions, /documents, /notifications, /profile, /performance) - 2026-10-05,
     * desormais renseignee pour tous les types. */
    static String route(PartenaireNotification n) {
        return switch (n.getType()) {
            case COMMISSION_DISPONIBLE, COMMISSION_GAGNEE, COMMISSION_VERSEE, DEMANDE_PAIEMENT_PAYEE, DEMANDE_PAIEMENT_ECHEC,
                 COMMISSION_COMMERCIALE_PAYEE -> "/commissions";
            case BONUS_VALIDE, BONUS_PAYE -> "/performance";
            case ECHEANCE_PROCHE, PRIME_NON_REVERSEE -> "CONTRAT".equals(objetType(n)) && objetId(n) != null
                    ? "/contracts/" + objetId(n) : "/contracts";
            case DOCUMENT_EXPIRE_BIENTOT, DOCUMENT_EXPIRE, DOCUMENT_REFUSE -> "/documents";
            case NOUVELLE_CONNEXION -> "/profile";
        };
    }

    // ---- Centre de notifications de l'utilisateur connecte (sans id dans l'URL, 2026-09-26) ----

    /** Destinataire du compte connecte : son partenaire s'il en a un, sinon l'utilisateur lui-meme
     * (commercial interne - 2026-10-03). Jamais d'erreur : un compte sans notification a une page vide. */
    private record Destinataire(Long partenaireId, Long userId) {
    }

    private Destinataire destinataireConnecte() {
        Long userId = authService.currentPrincipal().userId();
        Long partenaireId = profileRepository.findByUserId(userId)
                .map(Profile::getId)
                .map(partenaireRepository::findByProfile_Id)
                .flatMap(partenaires -> partenaires.stream().findFirst())
                .map(Partenaire::getId)
                .orElse(null);
        return new Destinataire(partenaireId, partenaireId == null ? userId : null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PartenaireNotificationResponse> listerMesNotifications(boolean nonLuesSeulement, Integer page, Integer size) {
        Destinataire d = destinataireConnecte();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<PartenaireNotification> result = notificationRepository.findVisibles(d.partenaireId(), d.userId(), nonLuesSeulement,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.getContent().stream().map(PartenaireNotificationService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long compterMesNonLues() {
        Destinataire d = destinataireConnecte();
        return notificationRepository.countVisiblesNonLues(d.partenaireId(), d.userId());
    }

    @Transactional
    public PartenaireNotificationResponse marquerMaNotificationLue(Long notificationId) {
        PartenaireNotification notification = maNotification(notificationId);
        if (!notification.isLu()) {
            notification.setLu(true);
            notification.setLuAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int marquerToutesMesNotificationsLues() {
        Destinataire d = destinataireConnecte();
        return notificationRepository.marquerToutesVisiblesLues(d.partenaireId(), d.userId(), Instant.now());
    }

    /** Suppression par son destinataire (2026-10-03) : masquee de son centre de notifications, conservee
     * dans l'historique administrateur. 404 si elle ne lui appartient pas ou est deja supprimee. */
    @Transactional
    public void supprimerMaNotification(Long notificationId) {
        PartenaireNotification notification = maNotification(notificationId);
        notification.setSupprimee(true);
        notification.setSupprimeeAt(Instant.now());
        if (!notification.isLu()) {
            notification.setLu(true);
            notification.setLuAt(Instant.now());
        }
        notificationRepository.save(notification);
    }

    private PartenaireNotification maNotification(Long notificationId) {
        Destinataire d = destinataireConnecte();
        return notificationRepository.findVisible(notificationId, d.partenaireId(), d.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable: " + notificationId));
    }

    @Transactional(readOnly = true)
    public PageResponse<PartenaireNotificationResponse> lister(Long partenaireId, boolean nonLuesSeulement, Integer page, Integer size) {
        verifierAcces(partenaireId);
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PartenaireNotification> result = nonLuesSeulement
                ? notificationRepository.findByPartenaireIdAndLuFalse(partenaireId, pageRequest)
                : notificationRepository.findByPartenaireId(partenaireId, pageRequest);
        return new PageResponse<>(result.getContent().stream().map(PartenaireNotificationService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long compterNonLues(Long partenaireId) {
        verifierAcces(partenaireId);
        return notificationRepository.countByPartenaireIdAndLuFalse(partenaireId);
    }

    @Transactional
    public PartenaireNotificationResponse marquerLue(Long partenaireId, Long notificationId) {
        verifierAcces(partenaireId);
        PartenaireNotification notification = notificationRepository.findByIdAndPartenaireId(notificationId, partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable: " + notificationId));
        if (!notification.isLu()) {
            notification.setLu(true);
            notification.setLuAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int marquerToutesLues(Long partenaireId) {
        verifierAcces(partenaireId);
        return notificationRepository.marquerToutesLues(partenaireId, Instant.now());
    }

    /** Admin, ou le partenaire connecte lui-meme (meme mecanisme que CommissionPartialPaymentService :
     * relation reelle Partenaire.profile, pas de comparaison de role). */
    private void verifierAcces(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            return;
        }
        boolean estCePartenaire = profileRepository.findByUserId(principal.userId())
                .map(Profile::getId)
                .map(partenaireRepository::findByProfile_Id)
                .map(partenaires -> partenaires.stream().anyMatch(p -> p.getId().equals(partenaireId)))
                .orElse(false);
        if (!estCePartenaire) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    private static PartenaireNotificationResponse toResponse(PartenaireNotification n) {
        return new PartenaireNotificationResponse(n.getId(), n.getType(), n.getTitre(), n.getMessage(), n.getMontant(),
                n.getContractId(), n.isSmsEnvoye(), n.isLu(), n.getLuAt(), n.getCreatedAt(), n.getPushStatus(),
                objetType(n), objetId(n), route(n));
    }
}
