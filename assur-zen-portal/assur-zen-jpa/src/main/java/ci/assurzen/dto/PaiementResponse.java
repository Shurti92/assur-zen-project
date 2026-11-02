package ci.assurzen.dto;

import java.time.Instant;
import java.time.LocalDate;

public record PaiementResponse(
        Long id,
        Long contractId,
        String policyNumber,
        Long montant,
        LocalDate datePaiement,
        String moyenPaiement,
        String reference,
        Long recordedByUserId,
        String recordedByUsername,
        Instant creation,
        Long echeanceId
) {
}
