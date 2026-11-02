package ci.assurzen.services;

import ci.assurzen.entities.CommissionPartialPaymentRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avances deja versees a UN partenaire par des demandes de paiement partiel payees, a imputer sur ses
 * contrats payes ensuite par l'admin (2026-09-26). Les plus anciennes sont consommees en premier.
 * Reservation en memoire pendant le traitement d'un paiement : liberee si le versement echoue,
 * persistee (montantImpute) seulement si le paiement est enregistre.
 */
final class AvancesPartenaire {

    /** Allocation d'avance pour UN contrat : id de la demande -> montant pris sur cette demande. */
    /** totalComptabilise : part de total prise sur des avances deja ecrites au grand livre - a retirer de
     * l'ecriture de commission du contrat pour ne pas la compter deux fois. */
    record Reservation(Map<Long, Long> parDemande, long total, long totalComptabilise) {
        static final Reservation AUCUNE = new Reservation(Map.of(), 0L, 0L);
    }

    private final List<long[]> restants = new ArrayList<>(); // [idDemande, restant, comptabilisee 0/1], ordre FIFO

    AvancesPartenaire(List<CommissionPartialPaymentRequest> demandesPayees) {
        for (CommissionPartialPaymentRequest demande : demandesPayees) {
            long restant = demande.avanceRestante();
            if (restant > 0) {
                restants.add(new long[]{demande.getId(), restant, demande.estComptabilisee() ? 1 : 0});
            }
        }
    }

    long disponible() {
        return restants.stream().mapToLong(r -> r[1]).sum();
    }

    /** Prend jusqu'a besoin sur les avances disponibles (FIFO). */
    Reservation reserver(long besoin) {
        Map<Long, Long> parDemande = new LinkedHashMap<>();
        long reste = Math.max(0L, besoin);
        long comptabilise = 0L;
        for (long[] r : restants) {
            if (reste == 0) {
                break;
            }
            long pris = Math.min(r[1], reste);
            if (pris > 0) {
                r[1] -= pris;
                reste -= pris;
                parDemande.put(r[0], pris);
                if (r[2] == 1) {
                    comptabilise += pris;
                }
            }
        }
        long total = parDemande.values().stream().mapToLong(Long::longValue).sum();
        return total == 0 ? Reservation.AUCUNE : new Reservation(parDemande, total, comptabilise);
    }

    /** Rend une reservation dont le versement a echoue. */
    void liberer(Reservation reservation) {
        reservation.parDemande().forEach((idDemande, montant) -> restants.stream()
                .filter(r -> r[0] == idDemande)
                .findFirst()
                .ifPresent(r -> r[1] += montant));
    }
}
