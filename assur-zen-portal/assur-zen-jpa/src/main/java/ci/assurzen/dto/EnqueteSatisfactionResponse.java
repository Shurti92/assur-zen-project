package ci.assurzen.dto;

import ci.assurzen.entities.EnqueteContexte;
import ci.assurzen.entities.EnqueteStatut;

import java.time.Instant;

public record EnqueteSatisfactionResponse(
        Long id,
        String code,
        EnqueteContexte contexte,
        Long insuredId,
        String insuredName,
        Long contratId,
        String contratPolicyNumber,
        Long sinistreId,
        String sinistreNumero,
        Instant dateEnvoi,
        Instant dateReponse,
        EnqueteStatut statut,
        Integer scoreNps,
        String categorieNps,
        String commentaire,
        Instant createdAt
) {
}
