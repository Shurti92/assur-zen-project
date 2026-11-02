package ci.assurzen.services;

import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.PartenaireDocument;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.events.NotificationUtilisateurEvent;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireDocumentRepository;
import ci.assurzen.repositories.PartenaireNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Rappels automatiques de l'Espace Partenaire (2026-10-04), lances chaque jour par
 * NotificationRappelScheduler. In-app + push selon les preferences, JAMAIS de SMS. Desactives tant que
 * NOTIFICATIONS_RAPPELS_AUTO_ENABLED n'est pas mis a true en back-office (nouveaux envois aux partenaires).
 * Chaque rappel n'est cree qu'une fois par objet et par jour (relance du serveur sans doublon) ; la prime
 * non reversee n'est rappelee qu'une seule fois.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRappelService {

    static final String CLE_ACTIVATION = "NOTIFICATIONS_RAPPELS_AUTO_ENABLED";
    static final String CLE_DELAI_PRIME = "PRIME_NON_REVERSEE_RAPPEL_JOURS";
    private static final ZoneId ZONE = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContractRepository contractRepository;
    private final PartenaireDocumentRepository documentRepository;
    private final PartenaireNotificationRepository notificationRepository;
    private final DynamicConfigurationService configuration;
    private final ApplicationEventPublisher eventPublisher;

    /** Renvoie le nombre de rappels emis (0 si desactive). */
    @Transactional(readOnly = true)
    public int executer(LocalDate aujourdhui) {
        if (!Boolean.TRUE.equals(configuration.getBoolean(CLE_ACTIVATION, false))) {
            return 0;
        }
        int emis = echeances(aujourdhui) + primesNonReversees(aujourdhui) + documents(aujourdhui);
        log.info("[RAPPELS-EP] {} rappel(s) emis le {}", emis, aujourdhui);
        return emis;
    }

    private int echeances(LocalDate aujourdhui) {
        int emis = 0;
        for (Contract c : contractRepository.findByStatusAndPartenaireIsNotNullAndDateEcheanceIn(ContractStatus.signed,
                List.of(aujourdhui.plusDays(30), aujourdhui.plusDays(7)))) {
            long jours = java.time.temporal.ChronoUnit.DAYS.between(aujourdhui, c.getDateEcheance());
            emis += emettre(c.getPartenaire().getId(), PartenaireNotificationType.ECHEANCE_PROCHE, "Échéance dans " + jours + " jours",
                    "Le contrat " + c.getPolicyNumber() + " arrive à échéance le " + DATE.format(c.getDateEcheance())
                            + ". Pensez à proposer son renouvellement.",
                    c.getPremium(), "CONTRAT", c.getId(), aujourdhui, false);
        }
        return emis;
    }

    private int primesNonReversees(LocalDate aujourdhui) {
        int delai = configuration.getInteger(CLE_DELAI_PRIME, 7);
        LocalDate cible = aujourdhui.minusDays(delai);
        int emis = 0;
        for (Contract c : contractRepository.findByStatusAndPrimeReversementStatusAndPartenaireIsNotNull(ContractStatus.signed,
                StatutReversementPrime.non_reverse)) {
            if (!cible.equals(DashboardService.dateSignature(c))) {
                continue;
            }
            emis += emettre(c.getPartenaire().getId(), PartenaireNotificationType.PRIME_NON_REVERSEE, "Prime à reverser",
                    "La prime de " + montant(c.getPremium()) + " FCFA du contrat " + c.getPolicyNumber()
                            + " n'a pas encore été reversée à la compagnie.",
                    c.getPremium(), "CONTRAT", c.getId(), aujourdhui, true);
        }
        return emis;
    }

    private int documents(LocalDate aujourdhui) {
        int emis = 0;
        for (PartenaireDocument d : documentRepository.findByDateExpirationIn(
                List.of(aujourdhui, aujourdhui.plusDays(7), aujourdhui.plusDays(30)))) {
            if (d.getPartenaire() == null) {
                continue;
            }
            boolean expire = d.getDateExpiration().equals(aujourdhui);
            emis += emettre(d.getPartenaire().getId(),
                    expire ? PartenaireNotificationType.DOCUMENT_EXPIRE : PartenaireNotificationType.DOCUMENT_EXPIRE_BIENTOT,
                    expire ? "Document expiré" : "Document bientôt expiré",
                    expire ? "Votre document " + d.getType() + " expire aujourd'hui. Merci d'en fournir un nouveau."
                            : "Votre document " + d.getType() + " expire le " + DATE.format(d.getDateExpiration()) + ".",
                    null, "DOCUMENT", d.getId(), aujourdhui, false);
        }
        return emis;
    }

    /** uneSeuleFois : jamais deux rappels de ce type pour cet objet ; sinon au plus un par jour. */
    private int emettre(Long partenaireId, PartenaireNotificationType type, String titre, String message, Long montant,
                        String objetType, Long objetId, LocalDate aujourdhui, boolean uneSeuleFois) {
        boolean deja = uneSeuleFois
                ? notificationRepository.existsByTypeAndObjetTypeAndObjetId(type, objetType, objetId)
                : notificationRepository.existsByTypeAndObjetTypeAndObjetIdAndCreatedAtGreaterThanEqual(type, objetType, objetId,
                        aujourdhui.atStartOfDay(ZONE).toInstant());
        if (deja) {
            return 0;
        }
        eventPublisher.publishEvent(NotificationUtilisateurEvent.pourPartenaire(partenaireId, type, titre, message, montant,
                objetType, objetId));
        return 1;
    }

    private static String montant(Long valeur) {
        return String.format("%,d", valeur == null ? 0L : valeur).replace(',', ' ');
    }
}
