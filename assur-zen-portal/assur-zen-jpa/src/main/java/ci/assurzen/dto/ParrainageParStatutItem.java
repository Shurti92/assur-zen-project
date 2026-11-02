package ci.assurzen.dto;

import ci.assurzen.entities.StatutParrainage;

public record ParrainageParStatutItem(
        StatutParrainage statut,
        long nombre
) {
}
