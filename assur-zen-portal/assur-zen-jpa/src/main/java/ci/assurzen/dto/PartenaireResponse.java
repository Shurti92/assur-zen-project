package ci.assurzen.dto;

import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.PartenaireType;

import java.math.BigDecimal;
import java.time.Instant;

public record PartenaireResponse(
        Long id,
        String codePartenaire,
        PartenaireType type,
        String nom,
        String prenoms,
        BigDecimal objectifProduction,
        BigDecimal objectifRealise,
        BigDecimal tauxRealisation,
        Long profileId,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        Long villePartenaireId,
        String villePartenaireNom,
        boolean active,
        Instant creation,
        Instant lastUpdate,
        Long typeProfileId,
        String telephone,
        String numeroWaveCommission,
        /** Non-null uniquement sur la reponse de create()/update() : indique si CETTE sauvegarde a declenche une tentative de creation de compte, et son resultat. Absent (null) si aucune tentative n'etait applicable. */
        Boolean compteCree,
        /** Present uniquement quand compteCree=false, motif de l'echec (ex. telephone deja utilise). */
        String compteCreationErreur,
        FrequencePaiementCommission frequencePaiementCommission,
        boolean accesActiviteCommerciale,
        boolean primeObjectifActivee,
        /** Documents joints a verifier (EN_ATTENTE, documents anterieurs inclus) - 2026-10-05, repere du back-office. */
        long documentsEnAttente
) {
}
