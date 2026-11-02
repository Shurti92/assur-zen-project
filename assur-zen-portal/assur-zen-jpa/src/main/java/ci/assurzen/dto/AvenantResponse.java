package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAvenant;
import ci.assurzen.entities.TypeAvenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AvenantResponse(
        Long id,
        Long contractId,
        String contractPolicyNumber,
        Integer numero,
        TypeAvenant type,
        CategorieAvenant categorie,
        LocalDate dateEffet,
        String motif,
        String description,
        Long ancienPremium,
        Long nouveauPremium,
        BigDecimal ancienneCommissionRate,
        BigDecimal nouvelleCommissionRate,
        Long ancienCommissionAmount,
        Long nouveauCommissionAmount,
        Long ancienPartnerShare,
        Long nouveauPartnerShare,
        Long ancienPartenaireId,
        String ancienPartenaireNom,
        Long nouveauPartenaireId,
        String nouveauPartenaireNom,
        Long ancienneCompagnieId,
        String ancienneCompagnieNom,
        Long nouvelleCompagnieId,
        String nouvelleCompagnieNom,
        Long createdById,
        String createdByUsername,
        Instant createdAt
) {
}
