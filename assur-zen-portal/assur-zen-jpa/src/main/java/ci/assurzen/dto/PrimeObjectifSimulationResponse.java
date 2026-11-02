package ci.assurzen.dto;

import java.math.BigDecimal;

/**
 * Apercu sans engagement (GET /partenaires/{id}/prime-objectif/simulation) - ne persiste rien.
 * "eligible" resume les 4 flags : true seulement si tous le sont, ce qui garantit qu'un POST
 * /primes-objectif immediatement apres reussirait (a une eventuelle course concurrente pres).
 */
public record PrimeObjectifSimulationResponse(
        Long partenaireId,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        BigDecimal objectifProduction,
        BigDecimal objectifRealise,
        BigDecimal tauxRealisation,
        Long montantSimule,
        boolean primeActivee,
        boolean exerciceCloture,
        boolean objectifAtteint,
        boolean demandeDejaExistante,
        boolean eligible
) {
}
