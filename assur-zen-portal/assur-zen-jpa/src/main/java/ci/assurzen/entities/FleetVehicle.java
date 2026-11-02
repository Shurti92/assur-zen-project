package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Ligne d'appartenance d'un Vehicule a une Fleet. Les lignes ne sont jamais supprimees, elles
 * transitent de statut (ACTIVE -> REMOVED/TRANSFERRED/REPLACED) pour conserver l'historique complet.
 * La colonne generee active_vehicule_id + sa cle unique (appliquees via script SQL manuel, cf.
 * sql/manual/) garantissent au niveau base qu'un vehicule ne peut avoir qu'une seule ligne ACTIVE a
 * la fois, tous fleets confondus - MySQL 8 n'a pas d'index unique partiel/filtre.
 */
@Entity
@Table(
        name = "t_assur_zen_fleet_vehicles",
        indexes = {
                @Index(name = "idx_fleet_vehicle_fleet", columnList = "fleet_id"),
                @Index(name = "idx_fleet_vehicle_vehicule", columnList = "vehicule_id"),
                @Index(name = "idx_fleet_vehicle_status", columnList = "status")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetVehicle extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_vehicle_fleet"))
    private Fleet fleet;

    /** @NotFound(IGNORE) (2026-09-13) : un vehicule supprime apres avoir quitte cette flotte (REMOVED/
     * TRANSFERRED/REPLACED, jamais ACTIVE - VehiculeService.delete() bloque deja la suppression d'un
     * vehicule encore actif dans une flotte) ne doit jamais faire planter GET /fleets/{id}/vehicles -
     * bug reel constate en direct (ObjectNotFoundException, le garde-fou "vehicule == null" existant
     * dans toVehicleResponse() ne suffisait pas contre une proxy pointant vers une ligne filtree par
     * @SQLRestriction). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "vehicule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_vehicle_vehicule"))
    private Vehicule vehicule;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FleetVehicleStatus status;

    @NotNull
    @Column(name = "date_added", nullable = false)
    private LocalDate dateAdded;

    @Column(name = "date_removed")
    private LocalDate dateRemoved;
}
