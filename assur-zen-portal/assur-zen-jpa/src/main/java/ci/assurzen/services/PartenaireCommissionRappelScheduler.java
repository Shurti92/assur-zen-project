package ci.assurzen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Rappels de commissions disponibles aux partenaires HEBDOMADAIRE (lundi), QUINZAINE (1er et 16) et
 * MENSUELLE (1er du mois), chaque jour a 9h heure d'Abidjan (2026-09-23). Le filtrage par jour est fait
 * par PartenaireCommissionAutomationService.frequencesDues. */
@Component
@RequiredArgsConstructor
public class PartenaireCommissionRappelScheduler {

    private static final ZoneId ZONE = ZoneId.of("Africa/Abidjan");

    private final PartenaireCommissionAutomationService automationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Abidjan")
    public void envoyerRappelsDuJour() {
        automationService.envoyerRappels(LocalDate.now(ZONE));
    }
}
