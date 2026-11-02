package ci.assurzen.dto;

import ci.assurzen.entities.TypeCompte;

public record BalanceCompteResponse(
        Long compteId,
        String compteCode,
        String compteLibelle,
        TypeCompte type,
        Long totalDebit,
        Long totalCredit,
        Long solde
) {
}
