package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationPriorite;

import java.time.LocalDate;

public record CrossSellRuleResponse(
        Long id,
        String code,
        String nom,
        String description,
        Long produitDeclencheurId,
        String produitDeclencheurNom,
        Long produitRecommandeId,
        String produitRecommandeNom,
        ReclamationPriorite priorite,
        Integer poidsScore,
        Integer ordre,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean actif
) {
}
