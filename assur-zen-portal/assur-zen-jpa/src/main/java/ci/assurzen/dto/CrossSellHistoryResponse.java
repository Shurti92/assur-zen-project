package ci.assurzen.dto;

import ci.assurzen.entities.CrossSellEvenementType;

import java.time.Instant;

public record CrossSellHistoryResponse(
        Long id,
        Long opportuniteId,
        CrossSellEvenementType typeEvenement,
        String description,
        Long auteurId,
        String auteurNom,
        Instant createdAt
) {
}
