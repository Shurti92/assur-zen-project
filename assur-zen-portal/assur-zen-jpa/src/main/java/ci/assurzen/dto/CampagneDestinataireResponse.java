package ci.assurzen.dto;

import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.StatutEnvoiDestinataire;

import java.time.Instant;

public record CampagneDestinataireResponse(
        Long id,
        Long campagneId,
        Long insuredId,
        String insuredName,
        CanalEnvoi canal,
        StatutEnvoiDestinataire statutEnvoi,
        Instant dateEnvoi,
        String erreurMessage,
        Instant createdAt
) {
}
