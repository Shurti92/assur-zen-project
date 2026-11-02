package ci.assurzen.dto;

import java.time.Instant;

/**
 * Entree unifiee de l'historique complet d'un partenaire (GET /partenaires/{id}/contracts/historique) :
 * combine les avenants structures (source = "AVENANT") et les entrees d'audit generique (source =
 * "AUDIT" - creation, modifications hors avenant, changements de statut...) de tous ses contrats, en
 * une seule timeline chronologique.
 */
public record HistoriqueContratResponse(
        Long contractId,
        String policyNumber,
        String source,
        String type,
        Integer numeroAvenant,
        String motif,
        String description,
        String champsModifies,
        Instant date,
        Long auteurId,
        String auteur
) {
}
