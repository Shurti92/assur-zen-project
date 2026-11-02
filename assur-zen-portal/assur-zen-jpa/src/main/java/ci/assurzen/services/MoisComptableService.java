package ci.assurzen.services;

import ci.assurzen.dto.MoisComptableResponse;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Decoupage mensuel des ExerciceComptable (contrat "Integration des mois comptables", 2026-08-28) :
 * generation automatique a la creation de l'exercice, cloture manuelle/auto independante de la
 * saisie d'ecritures (purement structurelle pour ce lot - cf. javadoc MoisComptable.cloture).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoisComptableService {

    private final MoisComptableRepository repository;
    private final ExerciceComptableRepository exerciceComptableRepository;

    /**
     * Backfill idempotent pour les exercices deja crees avant l'introduction de ce decoupage (ex.
     * "Exercice 2026" deja en base) - meme pattern que LedgerService.seedPlanComptable(). Ne genere
     * que pour les exercices qui n'ont encore aucun mois (jamais de double-generation).
     */
    @PostConstruct
    @Transactional
    public void genererMoisManquantsPourExercicesExistants() {
        exerciceComptableRepository.findAll().stream()
                .filter(exercice -> !repository.existsByExerciceComptableId(exercice.getId()))
                .forEach(this::genererMois);

        reconcilierClotureMoisAvecExercice();
    }

    /**
     * Auto-reparateur (execute a chaque demarrage, comme le backfill ci-dessus) : cloture tout mois
     * encore ouvert d'un exercice deja cloture. Corrige a la fois le drift historique (des mois
     * generes en cloture=false avant le fix de genererMois() ci-dessous, ex. "Exercice 2025" cloture
     * depuis le 2026-08-02 mais dont les mois n'ont ete backfilles - et donc restes ouverts - que le
     * 2026-08-28, bug signale par le frontend) et tout futur cas similaire (import manuel de donnees,
     * etc.), sans necessiter de correctif SQL ponctuel en prod : suffit de redemarrer l'app une fois
     * apres deploiement.
     */
    private void reconcilierClotureMoisAvecExercice() {
        exerciceComptableRepository.findAll().stream()
                .filter(ExerciceComptable::isCloture)
                .forEach(exercice -> cloturerTousLesMoisOuverts(exercice.getId()));
    }

    /** Un mois par tranche calendaire couverte par [dateDebut, dateFin] - premier/dernier mois
     * potentiellement partiels si l'exercice ne demarre/finit pas sur une frontiere de mois civil. */
    @Transactional
    public void genererMois(ExerciceComptable exercice) {
        if (repository.existsByExerciceComptableId(exercice.getId())) {
            return;
        }

        List<MoisComptable> mois = new java.util.ArrayList<>();
        YearMonth moisCourant = YearMonth.from(exercice.getDateDebut());
        YearMonth dernierMois = YearMonth.from(exercice.getDateFin());
        int numero = 1;

        while (!moisCourant.isAfter(dernierMois)) {
            LocalDate debutTranche = moisCourant.atDay(1).isBefore(exercice.getDateDebut())
                    ? exercice.getDateDebut() : moisCourant.atDay(1);
            LocalDate finTranche = moisCourant.atEndOfMonth().isAfter(exercice.getDateFin())
                    ? exercice.getDateFin() : moisCourant.atEndOfMonth();

            mois.add(MoisComptable.builder()
                    .exerciceComptable(exercice)
                    .numero(numero)
                    .libelle(libelleMois(moisCourant))
                    .dateDebut(debutTranche)
                    .dateFin(finTranche)
                    // Genere en cloture=true si l'exercice parent est deja cloture au moment de la
                    // generation (typiquement le backfill au demarrage sur un exercice deja clos avant
                    // l'introduction de ce decoupage) - sinon les mois d'un exercice deja verrouille
                    // naissaient a tort "ouverts", aucune cascade ulterieure ne les rattrapant jamais
                    // (bug reel signale par le frontend, 2026-08-28 : "Exercice 2025" cloture depuis le
                    // 2026-08-02, donc bien avant la creation de MoisComptable le 2026-08-28).
                    .cloture(exercice.isCloture())
                    .build());

            numero++;
            moisCourant = moisCourant.plusMonths(1);
        }

        repository.saveAll(mois);
        log.info("{} mois comptable(s) genere(s) pour l'exercice {}", mois.size(), exercice.getLibelle());
    }

    public List<MoisComptableResponse> listByExercice(Long exerciceComptableId) {
        return repository.findByExerciceComptableIdOrderByNumeroAsc(exerciceComptableId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MoisComptableResponse cloturer(Long exerciceComptableId, Long moisId) {
        MoisComptable mois = getMoisScope(exerciceComptableId, moisId);
        if (mois.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mois comptable deja cloture: " + mois.getLibelle());
        }
        mois.setCloture(true);
        return toResponse(repository.save(mois));
    }

    @Transactional
    public MoisComptableResponse reouvrir(Long exerciceComptableId, Long moisId) {
        MoisComptable mois = getMoisScope(exerciceComptableId, moisId);
        if (!mois.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mois comptable n'est pas cloture: " + mois.getLibelle());
        }
        mois.setCloture(false);
        return toResponse(repository.save(mois));
    }

    /** Cloture en cascade tous les mois encore ouverts d'un exercice (auto ou manuel, cf.
     * ExerciceComptableService.cloturer() et ExerciceComptableScheduler) - jamais l'inverse : rouvrir
     * l'exercice ne rouvre pas ses mois automatiquement (meme philosophie que ExerciceComptable.reouvrir()). */
    @Transactional
    public void cloturerTousLesMoisOuverts(Long exerciceComptableId) {
        List<MoisComptable> ouverts = repository.findByExerciceComptableIdAndClotureFalse(exerciceComptableId);
        if (ouverts.isEmpty()) {
            return;
        }
        ouverts.forEach(mois -> mois.setCloture(true));
        repository.saveAll(ouverts);
    }

    /** Cloture automatique quotidienne (ExerciceComptableScheduler) : tout mois dont dateFin est
     * depassee, independamment de l'etat de l'exercice parent. */
    @Transactional
    public void cloturerMoisExpires(LocalDate dateDuJour) {
        List<MoisComptable> expires = repository.findByClotureFalseAndDateFinBefore(dateDuJour);
        if (expires.isEmpty()) {
            return;
        }
        log.info("Cloture automatique de {} mois comptable(s) expire(s) au {}", expires.size(), dateDuJour);
        expires.forEach(mois -> mois.setCloture(true));
        repository.saveAll(expires);
    }

    private MoisComptable getMoisScope(Long exerciceComptableId, Long moisId) {
        MoisComptable mois = repository.findById(moisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisId));
        if (!mois.getExerciceComptable().getId().equals(exerciceComptableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisId);
        }
        return mois;
    }

    private String libelleMois(YearMonth yearMonth) {
        String nomMois = yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        String nomMoisCapitalise = nomMois.substring(0, 1).toUpperCase(Locale.FRENCH) + nomMois.substring(1);
        return nomMoisCapitalise + " " + yearMonth.getYear();
    }

    private MoisComptableResponse toResponse(MoisComptable mois) {
        return new MoisComptableResponse(
                mois.getId(),
                mois.getNumero(),
                mois.getLibelle(),
                mois.getDateDebut(),
                mois.getDateFin(),
                mois.isCloture()
        );
    }
}
