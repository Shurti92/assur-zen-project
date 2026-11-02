package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Flotte de vehicules (auto/moto/mixte) appartenant a un client (Insured). totalVehicles/
 * activeVehicles sont des compteurs denormalises pour la lecture rapide, mais ne sont JAMAIS
 * incrementes/decrementes en memoire - toujours recalcules par requete COUNT dans la meme
 * transaction que le mouvement qui les affecte (cf. FleetService), pour eviter la classe de bug de
 * derive de compteur deja rencontree dans ce projet (Prime Objectif, Dynamic Configuration).
 */
@Entity
@Table(
        name = "t_assur_zen_fleets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fleet_number", columnNames = "fleet_number")
        },
        indexes = {
                @Index(name = "idx_fleet_client", columnList = "client_id"),
                @Index(name = "idx_fleet_status", columnList = "status"),
                @Index(name = "idx_fleet_produit", columnList = "produit_id")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fleet extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "fleet_number", nullable = false, unique = true, length = 20)
    private String fleetNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_client"))
    private Insured client;

    @NotBlank
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Nullable : aucun produit de type FLOTTE (ProductType.code) n'existe encore en configuration
     * au moment de l'ajout de ce champ - le rendre obligatoire aurait bloque toute creation de
     * flotte. Pas de contrainte imposant que le produit choisi soit bien de type FLOTTE (demande
     * frontend explicite : filtrage cote selecteur uniquement, pas d'application cote backend). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", foreignKey = @ForeignKey(name = "fk_fleet_produit"))
    private Produit produit;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "fleet_type", nullable = false, length = 10)
    private FleetType fleetType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private FleetStatus status = FleetStatus.DRAFT;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 150)
    @Column(name = "manager_name", length = 150)
    private String managerName;

    @Size(max = 30)
    @Column(name = "manager_phone", length = 30)
    private String managerPhone;

    @Email
    @Size(max = 150)
    @Column(name = "manager_email", length = 150)
    private String managerEmail;

    @NotNull
    @Builder.Default
    @Column(name = "total_vehicles", nullable = false)
    private Integer totalVehicles = 0;

    @NotNull
    @Builder.Default
    @Column(name = "active_vehicles", nullable = false)
    private Integer activeVehicles = 0;
}
