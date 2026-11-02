package ci.assurzen.services;

import ci.assurzen.dto.ExerciceComptableRequest;
import ci.assurzen.dto.ExerciceComptableResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExerciceComptableService {

    private final ExerciceComptableRepository repository;
    private final ContractRepository contractRepository;
    private final MoisComptableService moisComptableService;

    public List<ExerciceComptableResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "dateDebut"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ExerciceComptableResponse> findAllActifs() {
        return repository.findByActiveTrue(Sort.by(Sort.Direction.DESC, "dateDebut"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ExerciceComptableResponse findById(Long id) {
        return toResponse(getExerciceComptable(id));
    }

    @Transactional
    public ExerciceComptableResponse create(ExerciceComptableRequest request) {
        validateDates(request);
        repository.findByLibelle(request.libelle())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un exercice comptable existe deja pour ce libelle: " + request.libelle());
                });

        ExerciceComptable exercice = ExerciceComptable.builder()
                .libelle(request.libelle())
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .active(request.active() == null || request.active())
                .objectifGlobal(request.objectifGlobal())
                .build();

        exercice = repository.save(exercice);
        moisComptableService.genererMois(exercice);
        return toResponse(exercice);
    }

    public ExerciceComptableResponse update(Long id, ExerciceComptableRequest request) {
        validateDates(request);
        ExerciceComptable exercice = getExerciceComptable(id);
        refuserSiCloture(exercice, "modifier");

        repository.findByLibelle(request.libelle())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un exercice comptable existe deja pour ce libelle: " + request.libelle());
                });

        exercice.setLibelle(request.libelle());
        exercice.setDateDebut(request.dateDebut());
        exercice.setDateFin(request.dateFin());
        exercice.setActive(request.active() == null || request.active());
        exercice.setObjectifGlobal(request.objectifGlobal());

        return toResponse(repository.save(exercice));
    }

    /**
     * Definit (ou met a jour) uniquement l'objectif de production global de l'exercice, sans toucher
     * aux autres champs (libelle/dates/active). Endpoint dedie pour ne pas exiger de repasser toutes
     * les infos de l'exercice juste pour fixer un objectif.
     */
    public ExerciceComptableResponse definirObjectifGlobal(Long id, BigDecimal objectifGlobal) {
        ExerciceComptable exercice = getExerciceComptable(id);
        refuserSiCloture(exercice, "modifier l'objectif de");
        exercice.setObjectifGlobal(objectifGlobal);
        return toResponse(repository.save(exercice));
    }

    public void delete(Long id) {
        ExerciceComptable exercice = getExerciceComptable(id);
        refuserSiCloture(exercice, "supprimer");
        repository.delete(exercice);
    }

    /**
     * Cloture l'exercice : verrouillage definitif, plus aucune ecriture comptable ne pourra y etre
     * journalisee (voir LedgerService). Action distincte de la desactivation automatique par date.
     */
    @Transactional
    public ExerciceComptableResponse cloturer(Long id) {
        ExerciceComptable exercice = getExerciceComptable(id);
        if (exercice.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercice comptable deja cloture: " + exercice.getLibelle());
        }
        exercice.setCloture(true);
        exercice.setActive(false);
        exercice = repository.save(exercice);
        // Cascade sur les mois encore ouverts (auto ou manuel, decision produit du 2026-08-28) - jamais
        // l'inverse : reouvrir() ci-dessous ne reouvre pas les mois automatiquement.
        moisComptableService.cloturerTousLesMoisOuverts(exercice.getId());
        return toResponse(exercice);
    }

    /**
     * Reouvre un exercice precedemment cloture (correction exceptionnelle). Ne reactive pas
     * automatiquement l'exercice : a faire explicitement si necessaire.
     */
    public ExerciceComptableResponse reouvrir(Long id) {
        ExerciceComptable exercice = getExerciceComptable(id);
        if (!exercice.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercice comptable n'est pas cloture: " + exercice.getLibelle());
        }
        exercice.setCloture(false);
        return toResponse(repository.save(exercice));
    }

    private void refuserSiCloture(ExerciceComptable exercice, String action) {
        if (exercice.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de " + action + " l'exercice comptable " + exercice.getLibelle() + " : il est cloture");
        }
    }

    private void validateDates(ExerciceComptableRequest request) {
        if (!request.dateFin().isAfter(request.dateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit etre posterieure a la date de debut");
        }
    }

    private ExerciceComptable getExerciceComptable(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice comptable introuvable: " + id));
    }

    private ExerciceComptableResponse toResponse(ExerciceComptable exercice) {
        BigDecimal objectifRealiseGlobal = computeObjectifRealiseGlobal(exercice);
        BigDecimal tauxRealisationGlobal = computeTauxRealisation(objectifRealiseGlobal, exercice.getObjectifGlobal());

        return new ExerciceComptableResponse(
                exercice.getId(),
                exercice.getLibelle(),
                exercice.getDateDebut(),
                exercice.getDateFin(),
                exercice.isActive(),
                exercice.isCloture(),
                exercice.getObjectifGlobal(),
                objectifRealiseGlobal,
                tauxRealisationGlobal,
                exercice.getCreation(),
                exercice.getLastUpdate(),
                moisComptableService.listByExercice(exercice.getId())
        );
    }

    /**
     * Somme des primes de tous les contrats signes durant cet exercice (tous partenaires/commerciaux
     * confondus), coherent avec le calcul individuel de Partenaire.objectifRealise.
     */
    private BigDecimal computeObjectifRealiseGlobal(ExerciceComptable exercice) {
        return contractRepository.findByStatusAndExerciceComptableId(ContractStatus.signed, exercice.getId()).stream()
                .map(Contract::getPremium)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computeTauxRealisation(BigDecimal objectifRealise, BigDecimal objectifGlobal) {
        if (objectifGlobal == null || objectifGlobal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return objectifRealise
                .divide(objectifGlobal, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
