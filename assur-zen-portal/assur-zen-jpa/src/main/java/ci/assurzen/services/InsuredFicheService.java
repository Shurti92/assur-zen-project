package ci.assurzen.services;

import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.InsuredFicheResponse;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.SinistreResponse;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.repositories.ExerciceComptableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alimente GET /insureds/{id}/fiche-sinistralite : vue 360 d'un assure, croisant l'integralite de son
 * historique de contrats et sa sinistralite, filtrable par exercice comptable.
 */
@Service
@RequiredArgsConstructor
public class InsuredFicheService {

    private static final Set<SinistreStatus> STATUTS_CLOTURES = Set.of(SinistreStatus.cloture, SinistreStatus.rejete);

    private final InsuredService insuredService;
    private final ContractsService contractsService;
    private final SinistreService sinistreService;
    private final ExerciceComptableRepository exerciceComptableRepository;

    public InsuredFicheResponse getFiche(Long insuredId, Long exerciceComptableId) {
        InsuredResponse insured = insuredService.findById(insuredId);
        ExerciceComptable exercice = resolveExercice(exerciceComptableId);

        List<ContractResponse> tousLesContrats = contractsService.findAllByInsuredId(insuredId);
        List<ContractResponse> contrats = exercice == null
                ? tousLesContrats
                : tousLesContrats.stream()
                        .filter(c -> exercice.getId().equals(c.exerciceComptableId()))
                        .toList();

        Set<Long> contractIds = contrats.stream().map(ContractResponse::id).collect(Collectors.toSet());

        List<SinistreResponse> tousLesSinistres = sinistreService.findAllByInsuredId(insuredId);
        List<SinistreResponse> sinistres = exercice == null
                ? tousLesSinistres
                : tousLesSinistres.stream()
                        .filter(s -> contractIds.contains(s.contractId()))
                        .toList();

        return new InsuredFicheResponse(
                insured.id(),
                insured.nom(),
                insured.prenoms(),
                insured.clientPhone(),
                insured.clientPhone2(),
                insured.clientCity(),
                exercice == null ? null : exercice.getId(),
                exercice == null ? null : exercice.getLibelle(),
                contrats,
                sinistres,
                computeStats(contrats, sinistres)
        );
    }

    private InsuredFicheResponse.Stats computeStats(List<ContractResponse> contrats, List<SinistreResponse> sinistres) {
        long contratsSignes = contrats.stream().filter(c -> c.status() == ContractStatus.signed).count();
        long totalPrimes = contrats.stream()
                .filter(c -> c.status() == ContractStatus.signed)
                .mapToLong(ContractResponse::premium)
                .sum();

        long sinistresClotures = sinistres.stream().filter(s -> STATUTS_CLOTURES.contains(s.status())).count();
        long totalMontantEstime = sinistres.stream()
                .mapToLong(s -> s.montantEstime() == null ? 0L : s.montantEstime())
                .sum();
        long totalMontantIndemnise = sinistres.stream()
                .mapToLong(s -> s.montantIndemnise() == null ? 0L : s.montantIndemnise())
                .sum();

        double tauxSinistralite = totalPrimes == 0 ? 0d : (double) totalMontantIndemnise / (double) totalPrimes;

        return new InsuredFicheResponse.Stats(
                contrats.size(),
                contratsSignes,
                sinistres.size(),
                sinistres.size() - sinistresClotures,
                sinistresClotures,
                totalPrimes,
                totalMontantEstime,
                totalMontantIndemnise,
                tauxSinistralite
        );
    }

    private ExerciceComptable resolveExercice(Long exerciceComptableId) {
        if (exerciceComptableId == null) {
            return null;
        }
        return exerciceComptableRepository.findById(exerciceComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice comptable introuvable: " + exerciceComptableId));
    }
}
