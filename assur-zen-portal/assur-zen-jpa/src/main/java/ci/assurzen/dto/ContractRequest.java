package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ModePaiement;
import ci.assurzen.entities.PeriodiciteEcheance;
import ci.assurzen.entities.StatutReversementPrime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractRequest(
        @Size(max = 50) String policyNumber,
        LocalDate dateEffet,
        @NotBlank @Size(max = 10) String duree,
        LocalDate dateEcheance,
        @NotNull Long productId,
        @NotNull Long insuredId,
        Long vehiculeId,
        Long partenaireId,
        @NotNull Long compagnieId,
        @NotNull Long premium,
        BigDecimal commissionRate,
        Long commissionAmount,
        /** Commission partenaire (FCFA entiers). Fournie = conservee telle quelle ; null = calculee
         * (premium - commissionAmount). Ignoree (0) sans partenaire. Decision utilisateur 2026-09-25. */
        Long partnerShare,
        ContractStatus status,
        CommissionStatus commissionStatus,
        LocalDate signedAt,
        Long salesRepId,
        String notes,
        Long exerciceComptableId,
        ModePaiement modePaiement,
        Integer nombreEcheances,
        PeriodiciteEcheance periodiciteEcheances,
        Long montantDejaVerse,
        Long contratPrecedentId,
        /** Facultatif - uniquement pertinent si le contrat est rattache a un partenaire. Permet de
         * declarer des la saisie que la prime encaissee a deja ete reversee a la compagnie (ex: prime
         * payee directement par le client, pas encaissee par le partenaire), sans passer par l'action
         * dediee POST /contracts/{id}/prime/reverser. Null = a la creation, non_reverse si un partenaire
         * ou un commercial est lie au contrat, reverse sinon (2026-09-25) ; en modification, valeur inchangee. */
        StatutReversementPrime primeReversementStatus,
        /** Commission du commercial (FCFA entiers), vente directe uniquement. Fournie = conservee ;
         * null = resolue depuis produits-commission-commercial (0 sans configuration). Refusee (400)
         * si non nulle alors qu'un partenaire est lie au contrat. */
        Long commercialCommissionAmount
) {
}
