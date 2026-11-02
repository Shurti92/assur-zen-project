package ci.assurzen.dto;

import ci.assurzen.entities.PeriodiciteEcheance;

import java.time.LocalDate;

public record ContractFractionneSummaryResponse(
        Long contractId,
        String policyNumber,
        String insuredName,
        Long premium,
        Integer nombreEcheances,
        PeriodiciteEcheance periodiciteEcheances,
        long echeancesPayees,
        long echeancesEnAttente,
        long echeancesEnRetard,
        Long montantPaye,
        Long montantRestantDu,
        LocalDate prochaineEcheanceDate
) {
}
