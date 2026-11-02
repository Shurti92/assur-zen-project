package ci.assurzen.services;

import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.repositories.ExerciceComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Synchronise quotidiennement le statut des exercices comptables (et de leurs mois, cf.
 * MoisComptableService) :
 * - cloture automatiquement (verrouillage definitif) tout exercice dont la date de fin est depassee et
 *   pas deja cloture, sans delai de grace (decision produit du 2026-08-28) - la cloture manuelle via
 *   POST /exercices-comptables/{id}/cloturer reste possible a tout moment independamment de ceci, et un
 *   exercice deja cloture manuellement avant sa date de fin est simplement ignore ici (idempotent)
 * - cloture en cascade tous les mois encore ouverts de ces exercices, et independamment tout mois dont
 *   sa propre date de fin est depassee (meme si l'exercice parent n'est pas encore termine)
 * - active automatiquement celui dont la periode contient desormais la date du jour (le nouvel exercice
 *   courant), uniquement s'il n'est pas cloture
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciceComptableScheduler {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");

    private final ExerciceComptableRepository repository;
    private final MoisComptableService moisComptableService;

    @Scheduled(cron = "0 1 0 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void synchroniserExercices() {
        LocalDate dateDuJour = LocalDate.now(ZONE_ID);
        moisComptableService.cloturerMoisExpires(dateDuJour);
        cloturerExercicesExpires(dateDuJour);
        activerExerciceCourant(dateDuJour);
    }

    private void cloturerExercicesExpires(LocalDate dateDuJour) {
        List<ExerciceComptable> expires = repository.findByClotureFalseAndDateFinBefore(dateDuJour);
        if (expires.isEmpty()) {
            return;
        }

        log.info("Cloture automatique de {} exercice(s) comptable(s) expire(s) au {}", expires.size(), dateDuJour);
        expires.forEach(exercice -> {
            exercice.setCloture(true);
            exercice.setActive(false);
        });
        repository.saveAll(expires);
        expires.forEach(exercice -> moisComptableService.cloturerTousLesMoisOuverts(exercice.getId()));
    }

    private void activerExerciceCourant(LocalDate dateDuJour) {
        List<ExerciceComptable> courants = repository
                .findByDateDebutLessThanEqualAndDateFinGreaterThanEqual(dateDuJour, dateDuJour)
                .stream()
                .filter(exercice -> !exercice.isActive() && !exercice.isCloture())
                .toList();

        if (courants.isEmpty()) {
            return;
        }

        log.info("Activation de {} exercice(s) comptable(s) devenu(s) courant(s) au {}", courants.size(), dateDuJour);
        courants.forEach(exercice -> exercice.setActive(true));
        repository.saveAll(courants);
    }
}
