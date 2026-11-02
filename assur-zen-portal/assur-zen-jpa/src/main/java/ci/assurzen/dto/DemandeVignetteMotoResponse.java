package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DemandeVignetteMotoResponse(
        Long id,
        String uuid,
        String reference,
        TypeDemandeVignette typeDemande,
        StatutDemandeVignetteMoto statut,

        String nomAssure,
        String prenomsAssure,
        String telephoneAssure,
        String telephoneAssureSecondaire,
        String adresseLivraison,
        String villeLivraison,
        String zoneLivraisonCode,

        String marqueMoto,
        String modeleMoto,
        String numeroChassisMoto,
        Integer cylindreeMoto,
        LocalDate datePremiereMiseCirculation,

        CategorieAgeMoto categorieAgeAppliquee,
        CategorieCylindree categorieCylindreeAppliquee,
        Integer ageMotoCalcule,

        Long tarifVignetteMotoId,
        BigDecimal montantVignette,
        BigDecimal fraisOperation,
        BigDecimal fraisLivraison,
        BigDecimal fraisAccessoires,
        BigDecimal montantTotal,
        BigDecimal reductionApporteur,

        String motifRejet,

        Long partenaireApporteurId,
        String partenaireApporteurNom,
        Long commercialApporteurId,
        String commercialApporteurNom,

        Instant createdAt,
        Instant updatedAt
) {
}
