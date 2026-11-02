package ci.assurzen.dto;

import java.time.LocalDate;

/**
 * Une barre du graphique "historique de production" (GET /dashboard/production-history) : un mois
 * comptable, production = prime des contrats signes dans le mois (date de signature entre dateDebut et
 * dateFin : signedAt, a defaut createdAt - meme base que GET /dashboard/kpis), du plus ancien au plus
 * recent. Commercial interne : production de sa performance mensuelle (base du bonus, signedAt seul).
 */
public record ProductionHistoryEntry(
        Long moisComptableId,
        /** "2026-10" (annee-mois de dateDebut). */
        String mois,
        /** Libelle du mois comptable, ex. "Octobre 2026". */
        String moisComptableLabel,
        LocalDate dateDebut,
        LocalDate dateFin,
        long production,
        long nbContrats,
        /** null si aucun objectif ne s'applique a ce mois. */
        Long objectif,
        /** MENSUEL (objectif mensuel du commercial) | ANNUEL_PRORATISE (objectif annuel du partenaire
         * divise par le nombre de mois de son exercice) | null si objectif null. */
        String objectifSource,
        /** Mois comptable en cours (aujourd'hui entre dateDebut et dateFin). */
        boolean enCours
) {
}
