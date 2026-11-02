package ci.assurzen.dto;

import java.time.Instant;
import java.util.List;

/**
 * Releve mensuel des commissions VERSEES a un partenaire (2026-10-03, refonte Espace Partenaire) : mois
 * calendaire (UTC) de la date de versement. Argent reellement verse dans le mois = commissions payees par
 * contrat (hors part deja couverte par une avance) + avances (demandes de paiement partiel) payees.
 */
public record CommissionStatementResponse(
        /** "2026-09". */
        String mois,
        /** Nombre de contrats dont la commission a ete payee dans le mois. */
        long nbContrats,
        /** Somme des commissions des contrats payes dans le mois (part partenaire). */
        long montantCommissions,
        /** Part de ces commissions deja versee auparavant par des avances (imputee, pas reversee). */
        long avancesImputees,
        /** Avances (demandes de paiement partiel) payees dans le mois. */
        long avancesVersees,
        /** Argent reellement verse dans le mois = montantCommissions - avancesImputees + avancesVersees. */
        long montantVerse,
        /** Date du dernier versement du mois. */
        Instant dateVersement,
        /** EN_COURS (mois calendaire courant, peut encore evoluer) | CLOTURE. */
        String statut,
        /** Lignes du releve - seulement dans le detail d'un mois (null dans la liste). */
        List<Ligne> lignes
) {

    public record Ligne(
            Instant date,
            /** COMMISSION (paiement d'un contrat) | AVANCE (demande de paiement partiel payee). */
            String type,
            Long contractId,
            String policyNumber,
            /** COMMISSION : commission du contrat ; AVANCE : montant de l'avance. */
            long montant,
            /** COMMISSION : part couverte par une avance anterieure ; AVANCE : 0. */
            long avanceImputee,
            long montantVerse,
            String reference
    ) {
    }
}
