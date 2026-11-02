package ci.assurzen.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Rappels automatiques de l'Espace Partenaire chaque jour a 8h heure d'Abidjan (2026-10-04), si actives
 * en back-office (cf. NotificationRappelService). */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRappelScheduler {

    private static final ZoneId ZONE = ZoneId.of("Africa/Abidjan");

    private final NotificationRappelService service;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Abidjan")
    public void executerRappelsDuJour() {
        try {
            service.executer(LocalDate.now(ZONE));
        } catch (RuntimeException e) {
            log.error("[RAPPELS-EP] echec des rappels du jour : {}", e.getMessage(), e);
        }
    }
}
