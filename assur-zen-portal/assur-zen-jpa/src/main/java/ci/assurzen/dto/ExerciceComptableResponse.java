package ci.assurzen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExerciceComptableResponse(
        Long id,
        String libelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean active,
        boolean cloture,
        BigDecimal objectifGlobal,
        BigDecimal objectifRealiseGlobal,
        BigDecimal tauxRealisationGlobal,
        Instant creation,
        Instant lastUpdate,
        /** Decoupage mensuel de l'exercice (Janvier {annee}..Decembre {annee} pour un exercice calendaire),
         * genere automatiquement a la creation - contrat "Integration des mois comptables" (2026-08-28). */
        List<MoisComptableResponse> mois
) {
}
