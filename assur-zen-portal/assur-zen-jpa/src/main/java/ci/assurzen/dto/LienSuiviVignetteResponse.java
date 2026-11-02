package ci.assurzen.dto;

import java.time.Instant;

/**
 * Reponse admin apres generation/regeneration d'un lien de suivi - le SEUL moment ou le token brut
 * (non hache) apparait, jamais relu ensuite (LienSuiviVignette ne persiste que son hash).
 */
public record LienSuiviVignetteResponse(
        Long id,
        Long demandeVignetteMotoId,
        String tokenBrut,
        String lienComplet,
        Instant dateExpiration
) {
}
