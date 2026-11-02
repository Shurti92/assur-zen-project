package ci.assurzen.dto;

import ci.assurzen.entities.EchangeStatut;

import java.time.Instant;

public record RecompenseEchangeResponse(
        Long id,
        Long compteFideliteId,
        Long insuredId,
        String insuredName,
        Long recompenseId,
        String recompenseNom,
        Long pointsUtilises,
        EchangeStatut statut,
        Instant dateEchange,
        Instant dateTraitement,
        Long traiteParId,
        String traitePar,
        Instant createdAt
) {
}
