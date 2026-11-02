package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ModePaiement;
import ci.assurzen.entities.PeriodiciteEcheance;
import ci.assurzen.entities.StatutReversementPrime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ContractResponse(
        Long id,
        String policyNumber,
        LocalDate dateEffet,
        String duree,
        LocalDate dateEcheance,
        Long productId,
        String productName,
        Long insuredId,
        String insuredName,
        Long vehiculeId,
        String vehiculeImmatriculation,
        Long partenaireId,
        String partenaireName,
        Long compagnieId,
        String compagnieName,
        Long premium,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long partnerShare,
        ContractStatus status,
        CommissionStatus commissionStatus,
        LocalDate signedAt,
        Instant createdAt,
        Long salesRepId,
        String salesRep,
        String notes,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        ModePaiement modePaiement,
        Integer nombreEcheances,
        PeriodiciteEcheance periodiciteEcheances,
        Long montantDejaVerse,
        Long contratPrecedentId,
        String contratPrecedentPolicyNumber,
        Long contratRenouvellementId,
        String contratRenouvellementPolicyNumber,
        /** Champs client "a plat", en plus de insuredName/insuredId - alimentent le bouton d'appel
         * rapide de l'app partenaire. Repris directement de la fiche Insured liee (insured.prenoms/nom
         * pour rester coherent avec insuredName = prenoms + " " + nom, cf. ContractsService). */
        String clientFirstName,
        String clientLastName,
        String clientPhone,
        String clientCity,
        /** Suivi du reversement de la prime encaissee par le partenaire a la compagnie (cf.
         * StatutReversementPrime) - non pertinent si partenaireId est null. */
        StatutReversementPrime primeReversementStatus,
        /** Commission versee au commercial interne (salesRep) qui a vendu ce contrat, en plus de son
         * salaire+bonus mensuel - non nulle/positive uniquement pour une vente directe (partenaireId
         * null) sur un produit dote d'une commission commerciale configuree (cf. module Commercial). */
        BigDecimal commercialCommissionRate,
        Long commercialCommissionAmount,
        CommissionStatus commercialCommissionStatus
) {
}
