package ci.assurzen.dto;

import ci.assurzen.entities.TypeDemandeVignette;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DemandeVignetteMotoRequest(
        @NotNull TypeDemandeVignette typeDemande,

        @NotBlank @Size(max = 100) String nomAssure,
        @NotBlank @Size(max = 200) String prenomsAssure,
        @NotBlank @Size(max = 20) String telephoneAssure,
        @Size(max = 20) String telephoneAssureSecondaire,
        @NotBlank @Size(max = 255) String adresseLivraison,
        @NotBlank @Size(max = 120) String villeLivraison,
        @NotBlank String zoneLivraison,

        @NotBlank @Size(max = 60) String marqueMoto,
        @NotBlank @Size(max = 60) String modeleMoto,
        @NotBlank @Size(max = 50) String numeroChassisMoto,
        @NotNull @Positive Integer cylindreeMoto,
        @NotNull @PastOrPresent LocalDate datePremiereMiseCirculation,

        /** Apporteur de la vente (facultatif - une demande sans apporteur, ex. vente directe/walk-in,
         * est legitime). Exactement un des deux au maximum : renseigner les deux est rejete. */
        Long partenaireApporteurId,
        Long commercialApporteurId
) {
}
