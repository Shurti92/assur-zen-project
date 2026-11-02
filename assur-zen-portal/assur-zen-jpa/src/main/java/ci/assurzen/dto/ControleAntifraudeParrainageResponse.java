package ci.assurzen.dto;

import ci.assurzen.entities.StatutControleFraude;
import ci.assurzen.entities.TypeControleFraude;

import java.time.Instant;

public record ControleAntifraudeParrainageResponse(
        Long id,
        Long parrainageId,
        TypeControleFraude typeControle,
        StatutControleFraude statut,
        Integer score,
        String motif,
        Instant createdAt
) {
}
