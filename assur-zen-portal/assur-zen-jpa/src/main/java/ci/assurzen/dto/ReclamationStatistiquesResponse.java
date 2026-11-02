package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationCanal;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.ReclamationStatut;
import ci.assurzen.entities.ReclamationType;

import java.util.Map;

public record ReclamationStatistiquesResponse(
        long total,
        long ouvertes,
        long resolues,
        long fermees,
        long creeesAutomatiquement,
        Double delaiMoyenResolutionHeures,
        Map<ReclamationStatut, Long> parStatut,
        Map<ReclamationPriorite, Long> parPriorite,
        Map<ReclamationType, Long> parType,
        Map<ReclamationCanal, Long> parCanal
) {
}
