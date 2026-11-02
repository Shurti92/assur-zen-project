package ci.assurzen.dto;

import ci.assurzen.entities.CampagneCible;
import ci.assurzen.entities.CampagneType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CampagneMarketingRequest(
        @NotNull @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @NotNull CampagneType type,
        @NotNull CampagneCible cible,
        /** Ignore si templateId est fourni (le contenu vient alors du template). Sinon obligatoire — validé en service. */
        String messageContenu,
        @Size(max = 200) String sujetEmail,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        Long budgetPrevu,
        /** Requis si cible = SEGMENT_DYNAMIQUE. */
        Long segmentId,
        /** Si fourni, prévaut sur messageContenu/sujetEmail. */
        Long templateId
) {
}
