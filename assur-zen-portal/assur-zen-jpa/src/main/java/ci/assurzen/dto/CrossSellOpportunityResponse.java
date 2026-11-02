package ci.assurzen.dto;

import ci.assurzen.entities.CrossSellStatut;
import ci.assurzen.entities.ReclamationPriorite;

import java.time.Instant;

public record CrossSellOpportunityResponse(
        Long id,
        String code,
        Long insuredId,
        String insuredName,
        Long contratId,
        String contratPolicyNumber,
        Long produitActuelId,
        String produitActuelNom,
        Long produitRecommandeId,
        String produitRecommandeNom,
        Integer score,
        Long regleId,
        String regleNom,
        Long commercialId,
        String commercialNom,
        Long campagneId,
        String campagneNom,
        ReclamationPriorite priorite,
        CrossSellStatut statut,
        String motif,
        String commentaire,
        Instant dateAffectation,
        Instant dateTraitement,
        Instant dateCloture,
        Instant createdAt,
        Instant lastUpdate
) {
}
