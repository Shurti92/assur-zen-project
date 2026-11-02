package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record SimulationTarifVignetteMotoRequest(
        @NotNull @PastOrPresent LocalDate datePremiereMiseCirculation,
        @NotNull @Positive Integer cylindree,
        @NotBlank String zoneLivraison,
        /** Optionnels (contrat "Commission apporteur variable par zone", 2026-09-19) - si renseigne,
         * l'apercu reflete deja la reduction apporteur, evitant un ecart avec le montant reel a la
         * creation. Meme regle de mutuelle exclusivite que DemandeVignetteMotoRequest. */
        Long partenaireApporteurId,
        Long commercialApporteurId
) {
}
