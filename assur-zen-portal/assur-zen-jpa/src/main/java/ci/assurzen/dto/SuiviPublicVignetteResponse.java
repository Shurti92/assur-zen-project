package ci.assurzen.dto;

import ci.assurzen.entities.StatutDemandeVignetteMoto;

import java.time.Instant;
import java.util.List;

/**
 * Reponse du suivi public (GET /vignettes-moto/demandes/suivi-public?token=...) - deliberement
 * minimale : ni montant, ni numero de telephone, ni identifiant technique, ni document, jamais
 * d'information sur un autre assure (contrat "Suivi public securise par token", 2026-09-17).
 */
public record SuiviPublicVignetteResponse(
        String reference,
        StatutDemandeVignetteMoto statut,
        String libelleStatut,
        String motifRejet,
        Instant dateDerniereMiseAJour,
        List<Entree> historique
) {
    public record Entree(
            StatutDemandeVignetteMoto statut,
            String libelle,
            Instant date
    ) {
    }
}
