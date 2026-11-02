package ci.assurzen.services;

import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialObjectiveType;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.repositories.CommercialMonthlyObjectiveRepository;
import ci.assurzen.repositories.CommercialPerformanceRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * S'assure qu'une performance CALCULATED/ELIGIBLE/NOT_ELIGIBLE existe pour chaque commercial ACTIVE
 * des qu'un mois comptable vient de se clore (contrat "Module Commercial", 2026-09-07) - meme si le
 * commercial ou son manager n'a jamais ouvert le dashboard ce mois-la. Purement un filet de securite :
 * CommercialPerformanceService.dashboard()/getPerformance()/list() recalculent deja "en direct" a chaque
 * lecture tant que la ligne n'est pas VALIDATED, donc ce job est idempotent et sans risque de double
 * calcul. Pas de verrou distribue (une seule instance de deploiement pour ce projet, meme convention
 * assumee que LoanOverdueService/LoanReminderService).
 *
 * Rattrapage (correctif 2026-10-02) : la version precedente ne traitait QUE les mois clotures dont la
 * date de fin etait exactement la veille - un mois cloture plus tard (cas courant : la cloture
 * comptable intervient quelques jours apres la fin du mois) n'etait jamais calcule. Desormais, tout mois
 * cloture deja termine est aussi traite pour les commerciaux qui y avaient un objectif mensuel et n'ont
 * encore aucune ligne de performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommercialPerformanceScheduler {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");

    private final MoisComptableRepository moisComptableRepository;
    private final CommercialRepository commercialRepository;
    private final CommercialPerformanceService performanceService;
    private final CommercialPerformanceRepository performanceRepository;
    private final CommercialMonthlyObjectiveRepository monthlyObjectiveRepository;

    @Scheduled(cron = "0 15 0 * * *", zone = "Africa/Abidjan")
    public void calculerMoisVenantDeClore() {
        LocalDate hier = LocalDate.now(ZONE_ID).minusDays(1);
        List<MoisComptable> moisClosTermines = moisComptableRepository.findByClotureTrue().stream()
                .filter(mois -> mois.getDateFin() != null && !mois.getDateFin().isAfter(hier))
                .toList();
        if (moisClosTermines.isEmpty()) {
            return;
        }

        List<Commercial> actifs = commercialRepository.findByStatut(StatutCommercial.ACTIVE);
        int calcules = 0;
        for (MoisComptable mois : moisClosTermines) {
            boolean venantDeClore = mois.getDateFin().isEqual(hier);
            for (Commercial commercial : actifs) {
                if (venantDeClore || aRattraper(commercial, mois)) {
                    calculerSiPossible(commercial, mois);
                    calcules++;
                }
            }
        }
        if (calcules > 0) {
            log.info("Calcul de performance de fin de mois : {} calcul(s) sur {} mois comptable(s) clotures", calcules, moisClosTermines.size());
        }
    }

    /** Mois cloture plus ancien que la veille : uniquement si le commercial y avait un objectif et
     * qu'aucune ligne n'existe encore (jamais de recalcul d'une ligne deja persistee ici). */
    private boolean aRattraper(Commercial commercial, MoisComptable mois) {
        return performanceRepository.findByCommercialIdAndMoisComptableId(commercial.getId(), mois.getId()).isEmpty()
                && monthlyObjectiveRepository.findByCommercialIdAndMoisComptableIdAndObjectiveType(
                commercial.getId(), mois.getId(), CommercialObjectiveType.PREMIUM_AMOUNT).isPresent();
    }

    /** recalculerSysteme() est @Transactional sur CommercialPerformanceService (bean distinct) - l'appel
     * traverse bien le proxy Spring ici, contrairement a un self-invocation. */
    private void calculerSiPossible(Commercial commercial, MoisComptable mois) {
        try {
            performanceService.recalculerSysteme(commercial, mois);
        } catch (Exception e) {
            log.error("Echec du calcul de performance automatique pour le commercial {} sur {}", commercial.getId(), mois.getLibelle(), e);
        }
    }
}
