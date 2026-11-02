package ci.assurzen.dto;

import ci.assurzen.entities.TypeOperationLedger;

import java.time.LocalDate;

public record GrandLivreLineResponse(
        LocalDate dateEcriture,
        String ecritureReference,
        TypeOperationLedger typeOperation,
        String libelle,
        Long montantDebit,
        Long montantCredit,
        Long soldeProgressif
) {
}
