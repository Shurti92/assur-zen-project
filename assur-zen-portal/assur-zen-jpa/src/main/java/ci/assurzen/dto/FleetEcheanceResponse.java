package ci.assurzen.dto;

import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.VehiculeType;

import java.time.LocalDate;
import java.util.List;

/** Un contrat lie a un vehicule/moto actuellement incorpore dans la flotte, avec ses avis
 * d'echeance (programmes et deja envoyes) - GET /fleets/{id}/echeances. */
public record FleetEcheanceResponse(
        Long contractId,
        String policyNumber,
        ContractStatus status,
        Long vehiculeId,
        String immatriculation,
        String vehiculeMarque,
        String vehiculeModele,
        VehiculeType vehiculeType,
        LocalDate dateEffet,
        LocalDate dateEcheance,
        List<NotificateurSmsResponse> rappels
) {
}
