package ci.assurzen.services;

import ci.assurzen.events.NotificationUtilisateurEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cree la notification in-app APRES commit de l'operation metier (paiement, validation...) : si celle-ci
 * est annulee, aucune notification n'est creee ; si la creation de la notification echoue, l'operation
 * reste acquise (echec seulement journalise). fallbackExecution : aussi traitee hors transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationUtilisateurListener {

    private final PartenaireNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationUtilisateurEvent event) {
        try {
            notificationService.notifierInApp(event);
        } catch (RuntimeException e) {
            log.error("[NOTIFICATION] notification {} non creee (partenaire {}, utilisateur {}) : {}",
                    event.type(), event.partenaireId(), event.userId(), e.getMessage(), e);
        }
    }
}
