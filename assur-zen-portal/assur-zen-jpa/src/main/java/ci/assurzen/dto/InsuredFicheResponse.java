package ci.assurzen.dto;

import java.util.List;

/**
 * Fiche complete d'un assure : historique de tous les contrats jamais souscrits (depuis le debut) et
 * fiche de sinistralite associee, avec indicateurs agreges. Filtrable par exercice comptable (voir
 * InsuredFicheService) ; sans filtre, la fiche couvre l'integralite de l'historique.
 */
public record InsuredFicheResponse(
        Long insuredId,
        String nom,
        String prenoms,
        String clientPhone,
        String clientPhone2,
        String clientCity,
        Long exerciceComptableId,
        String exerciceComptableLibelle,
        List<ContractResponse> historiqueContrats,
        List<SinistreResponse> sinistres,
        Stats stats
) {

    public record Stats(
            long totalContrats,
            long contratsSignes,
            long totalSinistres,
            long sinistresEnCours,
            long sinistresClotures,
            long totalPrimes,
            long totalMontantEstime,
            long totalMontantIndemnise,
            double tauxSinistralite
    ) {
    }
}
