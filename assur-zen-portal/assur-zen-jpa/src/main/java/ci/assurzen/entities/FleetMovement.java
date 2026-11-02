package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Historique append-only des mouvements de vehicules au sein des flottes. N'etend PAS
 * AuditableEntity : aucune mise a jour ni verrou necessaire sur une ligne d'historique immuable.
 */
@Entity
@Table(
        name = "t_assur_zen_fleet_movements",
        indexes = {
                @Index(name = "idx_fleet_movement_fleet", columnList = "fleet_id"),
                @Index(name = "idx_fleet_movement_vehicule", columnList = "vehicule_id"),
                @Index(name = "idx_fleet_movement_type", columnList = "movement_type"),
                @Index(name = "idx_fleet_movement_date", columnList = "movement_date"),
                @Index(name = "idx_fleet_movement_destination_fleet", columnList = "destination_fleet_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetMovement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pour TRANSFER, il s'agit de la flotte SOURCE. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_movement_fleet"))
    private Fleet fleet;

    /** @NotFound(IGNORE) (2026-09-13) : un vehicule supprime (SQLRestriction deleted=false) apres avoir
     * quitte definitivement une flotte ne doit jamais faire planter la consultation de l'historique des
     * mouvements - la FK reste NOT NULL en base (l'evenement a bien eu lieu), seule la lecture cote
     * entite devient null si la cible n'est plus visible. Bug reel constate en direct (ObjectNotFoundException). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "vehicule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_movement_vehicule"))
    private Vehicule vehicule;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private FleetMovementType movementType;

    @NotNull
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Renseigne uniquement pour REPLACE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "old_vehicule_id", foreignKey = @ForeignKey(name = "fk_fleet_movement_old_vehicule"))
    private Vehicule oldVehicule;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "new_vehicule_id", foreignKey = @ForeignKey(name = "fk_fleet_movement_new_vehicule"))
    private Vehicule newVehicule;

    /** Renseignes uniquement pour TRANSFER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_fleet_id", foreignKey = @ForeignKey(name = "fk_fleet_movement_source_fleet"))
    private Fleet sourceFleet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_fleet_id", foreignKey = @ForeignKey(name = "fk_fleet_movement_destination_fleet"))
    private Fleet destinationFleet;

    /** Snapshot informatif du contrat actif sur le vehicule au moment du mouvement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", foreignKey = @ForeignKey(name = "fk_fleet_movement_contract"))
    private Contract contract;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_movement_performed_by"))
    private User performedBy;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Champ libre non columnise ailleurs - explicitement TEXT (pas @Lob nu, qui degenere en
     * TINYTEXT/255 caracteres sur ce projet, cf. t_assur_zen_audit_flux). */
    @Lob
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
