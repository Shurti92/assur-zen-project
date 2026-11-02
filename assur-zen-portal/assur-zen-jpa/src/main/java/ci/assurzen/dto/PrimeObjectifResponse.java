package ci.assurzen.dto;

import ci.assurzen.entities.StatutPrimeObjectif;

import java.math.BigDecimal;
import java.time.Instant;

public record PrimeObjectifResponse(
        Long id,
        String reference,
        Long partenaireId,
        String partenaireNom,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        BigDecimal objectifProduction,
        BigDecimal objectifRealise,
        BigDecimal tauxRealisation,
        Long montant,
        StatutPrimeObjectif statut,
        Instant dateDemande,
        Long demandeParUserId,
        Long valideParUserId,
        Instant dateValidation,
        Long payeParUserId,
        Instant datePaiement,
        String motifRejet,
        /** Non-null uniquement sur la reponse de la transition vers PAYEE. */
        Boolean notificationEnvoyee
) {
}
