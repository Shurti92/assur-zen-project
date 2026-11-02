package ci.assurzen.dto;

import ci.assurzen.entities.StatutEcheance;

import java.time.LocalDate;

public record EcheanceResponse(
        Long id,
        Long contractId,
        String policyNumber,
        Integer numeroEcheance,
        Long montant,
        LocalDate dateEcheance,
        StatutEcheance statut,
        boolean enRetard,
        Long paiementId
) {
}
