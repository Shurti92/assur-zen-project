package ci.assurzen.services;

import ci.assurzen.events.PartenaireNotificationCreeeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Push navigateur des notifications partenaire (2026-09-26) : APRES commit et en asynchrone, pour qu'un
 * service push lent ou en panne ne retarde ni n'annule jamais l'operation metier (signature de contrat,
 * virement confirme...) ni la notification in-app deja enregistree.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartenaireNotificationPushListener {

    private final PartenaireNotificationService notificationService;

    @Async("webPushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreee(PartenaireNotificationCreeeEvent event) {
        try {
            notificationService.envoyerPush(event);
        } catch (RuntimeException e) {
            log.error("[WEBPUSH] echec du push de la notification {} : {}", event.notificationId(), e.getClass().getSimpleName(), e);
        }
    }
}
