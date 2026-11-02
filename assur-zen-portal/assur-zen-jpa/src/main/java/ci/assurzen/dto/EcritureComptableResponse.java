package ci.assurzen.dto;

import ci.assurzen.entities.TypeOperationLedger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EcritureComptableResponse(
        Long id,
        String reference,
        LocalDate dateEcriture,
        String libelle,
        TypeOperationLedger typeOperation,
        Long contractId,
        String contractPolicyNumber,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        Long ecritureOrigineId,
        boolean contrepassee,
        List<LigneEcritureResponse> lignes,
        Instant creation
) {
}
