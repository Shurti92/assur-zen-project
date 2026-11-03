package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.MobileNotification;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MobileNotificationRepository;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Notifications in-app des comptes mobile (2026-11-03). Creees par le serveur lors d'evenements : changement de statut d'un
 * sinistre, demande de contrat traitee, paiement confirme ou echoue, reponse du support, echeance proche (J-30 et J-7).
 * Aucun texte n'est ecrit cote application. Conservation : NOTIFICATION_RETENTION_MONTHS (defaut 12). Une notification
 * ne doit jamais faire echouer l'action metier qui la declenche (erreurs journalisees et ignorees).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileNotificationService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final MobileNotificationRepository repository;
    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final ContractRepository contractRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FcmPushService pushService;

    // ------------------------------------------------------------------ creation (cote serveur)

    /** Notifie un compte mobile. dedupeKey (facultatif) evite de creer deux fois le meme evenement. */
    public void notifier(Long userId, String categorie, String titre, String corps, Long contractId, Long claimId, String dedupeKey) {
        if (userId == null) {
            return;
        }
        try {
            if (StringUtils.hasText(dedupeKey) && repository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
                return;
            }
            MobileNotification n = new MobileNotification();
            n.setUserId(userId);
            n.setCategory(categorie);
            n.setTitle(tronquer(titre, 150));
            n.setBody(tronquer(corps, 1000));
            n.setContractId(contractId);
            n.setClaimId(claimId);
            n.setDedupeKey(StringUtils.hasText(dedupeKey) ? dedupeKey : null);
            n = repository.save(n);
            pushService.diffuser(userId, categorie, n.getTitle(), n.getBody(), n.getId(), contractId, claimId);
        } catch (DataIntegrityViolationException e) {
            // doublon concurrent : deja notifie
        } catch (RuntimeException e) {
            log.warn("[MOBILE-NOTIF] notification non creee (utilisateur {}) : {}", userId, e.getMessage());
        }
    }

    /** Notifie tous les comptes mobile rattaches a un assure (sinistre, contrat...). */
    public void notifierAssure(Long insuredId, String categorie, String titre, String corps, Long contractId, Long claimId, String dedupeKey) {
        if (insuredId == null) {
            return;
        }
        try {
            for (ClientAccountInsured lien : clientAccountInsuredRepository.findByInsuredId(insuredId)) {
                notifier(lien.getUserId(), categorie, titre, corps, contractId, claimId, dedupeKey);
            }
        } catch (RuntimeException e) {
            log.warn("[MOBILE-NOTIF] notification de l'assure {} non creee : {}", insuredId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ lecture / lecture seule par le client

    @Transactional(readOnly = true)
    public MobileExtraDtos.NotificationPage lister(Long userId, int page, int size) {
        int taille = Math.min(Math.max(size, 1), 50);
        Page<MobileNotification> resultat = repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(page, 0), taille));
        return new MobileExtraDtos.NotificationPage(resultat.getContent().stream().map(this::vue).toList(),
                repository.countByUserIdAndReadAtIsNull(userId), resultat.getNumber(), taille, resultat.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.UnreadCount nonLues(Long userId) {
        return new MobileExtraDtos.UnreadCount(repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public MobileExtraDtos.NotificationItem marquerLue(Long userId, Long id) {
        MobileNotification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MobileApiException(404, "NOTIFICATION_NOT_FOUND", "Notification introuvable."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            repository.save(n);
        }
        return vue(n);
    }

    @Transactional
    public MobileExtraDtos.UnreadCount toutMarquerLu(Long userId) {
        repository.toutMarquerLu(userId, Instant.now());
        return nonLues(userId);
    }

    // ------------------------------------------------------------------ taches planifiees

    /** Echeances proches : notification a J-30 et J-7 pour les contrats signes non encore renouvelés (chaque jour a 8 h). */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void notifierEcheancesProches() {
        LocalDate aujourdhui = LocalDate.now(ABIDJAN);
        for (int jours : new int[]{30, 7}) {
            LocalDate echeance = aujourdhui.plusDays(jours);
            for (Contract c : contractRepository.findByDateEcheanceAndStatus(echeance, ContractStatus.signed)) {
                if (c.getInsured() == null || contractRepository.findByContratPrecedentId(c.getId()).isPresent()) {
                    continue;
                }
                notifierAssure(c.getInsured().getId(), "EXPIRY",
                        jours == 30 ? "Échéance dans 30 jours" : "Échéance dans 7 jours",
                        "Votre contrat " + c.getPolicyNumber() + " arrive à échéance le " + echeance + ".",
                        c.getId(), null, "EXP-" + c.getId() + "-" + echeance + "-J" + jours);
            }
        }
    }

    /** Purge des notifications plus anciennes que la duree de conservation (chaque nuit a 3 h 30). */
    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void purger() {
        long mois = Math.max(1, dynamicConfigurationService.getLong("NOTIFICATION_RETENTION_MONTHS", 12L));
        int supprimees = repository.supprimerAnterieures(ZonedDateTime.now(ABIDJAN).minusMonths(mois).toInstant());
        if (supprimees > 0) {
            log.info("[MOBILE-NOTIF] {} notification(s) anterieure(s) a {} mois supprimee(s)", supprimees, mois);
        }
    }

    // ------------------------------------------------------------------

    private MobileExtraDtos.NotificationItem vue(MobileNotification n) {
        return new MobileExtraDtos.NotificationItem(String.valueOf(n.getId()), n.getCategory(), n.getTitle(), n.getBody(), n.getCreatedAt(),
                n.getReadAt() != null, n.getContractId() == null ? null : String.valueOf(n.getContractId()),
                n.getClaimId() == null ? null : String.valueOf(n.getClaimId()));
    }

    private String tronquer(String texte, int max) {
        return texte != null && texte.length() > max ? texte.substring(0, max) : texte;
    }
}
