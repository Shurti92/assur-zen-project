package ci.assurzen.dto;

import ci.assurzen.entities.CampagneCible;
import ci.assurzen.entities.CampagneStatut;
import ci.assurzen.entities.CampagneType;

import java.time.Instant;
import java.time.LocalDate;

public record CampagneMarketingResponse(
        Long id,
        String code,
        String nom,
        String description,
        CampagneType type,
        CampagneStatut statut,
        CampagneCible cible,
        String messageContenu,
        String sujetEmail,
        LocalDate dateDebut,
        LocalDate dateFin,
        Long budgetPrevu,
        Long createdById,
        String createdBy,
        Long segmentId,
        String segmentNom,
        Long templateId,
        String templateNom,
        long nbDestinataires,
        long nbEnvoyes,
        long nbEchecs,
        Instant createdAt
) {
}
