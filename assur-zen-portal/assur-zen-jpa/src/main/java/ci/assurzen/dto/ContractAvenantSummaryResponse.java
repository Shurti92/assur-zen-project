package ci.assurzen.dto;

import ci.assurzen.entities.TypeAvenant;

import java.time.Instant;

/**
 * Ligne resumee pour "contrats ayant subi un avenant" (GET /contracts/avenants,
 * GET /partenaires/{id}/contracts/avenants) - un contrat peut avoir plusieurs avenants, ceci n'en montre
 * que l'agrege (nombre total + dernier). Le detail complet reste sur GET /contracts/{id}/avenants.
 */
public record ContractAvenantSummaryResponse(
        Long contractId,
        String policyNumber,
        Long partenaireId,
        String partenaireName,
        long nombreAvenants,
        Integer dernierNumeroAvenant,
        TypeAvenant dernierTypeAvenant,
        Instant dernierAvenantAt
) {
}
