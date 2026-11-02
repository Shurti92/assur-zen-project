package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PaiementRequest(
        @NotNull Long montant,
        LocalDate datePaiement,
        @Size(max = 100) String moyenPaiement,
        @Size(max = 255) String reference,
        Long echeanceId
) {
}
