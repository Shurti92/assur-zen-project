package ci.assurzen.dto;

import ci.assurzen.entities.SinistreStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SinistreResponse(
        Long id,
        String numero,
        Long contractId,
        String contractPolicyNumber,
        Long insuredId,
        String insuredName,
        String nature,
        LocalDate dateSurvenance,
        LocalDate dateDeclaration,
        Long montantEstime,
        Long montantIndemnise,
        SinistreStatus status,
        Long declarantId,
        String declarant,
        Long assigneeId,
        String assignee,
        Long compagnieId,
        String compagnieNom,
        String gestionnaireNom,
        String description,
        String notes,
        Instant createdAt
) {
}
