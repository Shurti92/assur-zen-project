package ci.assurzen.dto;

import ci.assurzen.entities.ContractStatus;

import java.time.LocalDate;

public record FleetContractResponse(
        Long contractId,
        String policyNumber,
        String produitNom,
        Long vehiculeId,
        String immatriculation,
        LocalDate dateEffet,
        LocalDate dateEcheance,
        Long premium,
        ContractStatus status
) {
}
