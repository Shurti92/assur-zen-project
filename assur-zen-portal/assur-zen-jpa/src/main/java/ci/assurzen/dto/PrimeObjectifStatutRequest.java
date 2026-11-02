package ci.assurzen.dto;

import ci.assurzen.entities.StatutPrimeObjectif;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrimeObjectifStatutRequest(
        @NotNull
        StatutPrimeObjectif statut,

        /** Obligatoire (verifie cote service) uniquement quand statut=REJETEE. */
        @Size(max = 500)
        String motifRejet
) {
}
