package ci.assurzen.dto;

import java.math.BigDecimal;

/**
 * Ligne de classement (§36) - volontairement depourvue de toute donnee salariale/bonus en montant
 * absolu (uniquement le taux, deja public au sein de l'equipe) : le classement ne doit jamais exposer
 * les salaires des autres commerciaux.
 */
public record CommercialRankingEntry(
        Long commercialId,
        String employeeCode,
        String nomComplet,
        BigDecimal achievementRate,
        /** Position dans le classement (1 = meilleur taux d'atteinte), deja calculee cote serveur -
         * ex demande frontend du 2026-09-08, evite de dupliquer cette logique cote client. */
        int rang
) {
}
