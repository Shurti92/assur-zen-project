package ci.assurzen.services;

import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.repositories.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Definition de la "production" d'un commercial (§22, contrat "Module Commercial", 2026-09-07) :
 * somme de Contract.premium pour les contrats status=signed dont Contract.salesRep est le User du
 * commercial, signes (signedAt) dans les bornes du MoisComptable donne. Meme definition et meme
 * source de verite que PartenaireService.computeObjectifRealise() (contrats signed, premium brut),
 * simplement filtree par salesRep au lieu de partenaire - Contract.salesRep est deja le pivot de
 * scoping "commercial" reutilise partout ailleurs dans le projet (ContractsService, DashboardService,
 * EcheancierService, etc.), donc pas de nouvelle relation Commercial-&gt;Contract.
 */
@Service
@RequiredArgsConstructor
public class CommercialProductionService {

    private final ContractRepository contractRepository;

    public BigDecimal computeRealise(Commercial commercial, MoisComptable mois) {
        return contratsEligibles(commercial, mois).stream()
                .map(Contract::getPremium)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long countContratsEligibles(Commercial commercial, MoisComptable mois) {
        return contratsEligibles(commercial, mois).size();
    }

    public List<Contract> contratsEligibles(Commercial commercial, MoisComptable mois) {
        Long salesRepId = commercial.getProfile().getUser().getId();
        return contractRepository.findByStatusAndSalesRepId(ContractStatus.signed, salesRepId).stream()
                .filter(contract -> isSignedWithinMois(contract, mois))
                .toList();
    }

    private boolean isSignedWithinMois(Contract contract, MoisComptable mois) {
        LocalDate signedAt = contract.getSignedAt();
        if (signedAt == null) {
            return false;
        }
        return !signedAt.isBefore(mois.getDateDebut()) && !signedAt.isAfter(mois.getDateFin());
    }

    /** Meme formule/arrondi que PartenaireService.computeTauxRealisation - null si objectif nul/absent. */
    public BigDecimal computeTauxRealisation(BigDecimal realise, BigDecimal objectif) {
        if (objectif == null || objectif.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return realise
                .divide(objectif, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
