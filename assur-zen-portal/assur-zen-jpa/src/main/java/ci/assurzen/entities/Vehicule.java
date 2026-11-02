package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Vehicule (auto ou moto) rattache a un client (Insured), reutilisable independamment d'une
 * flotte (un contrat peut referencer un Vehicule sans que celui-ci soit dans une flotte).
 * currentFleet est un cache de lecture uniquement, jamais l'autorite d'unicite : voir
 * FleetService (verrou pessimiste + contrainte DB sur t_assur_zen_fleet_vehicles) pour la garantie
 * "actif dans au plus une flotte a la fois".
 */
@Entity
@Table(
        name = "t_assur_zen_vehicules",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vehicule_immatriculation", columnNames = "immatriculation"),
                @UniqueConstraint(name = "uk_vehicule_chassis", columnNames = "numero_chassis")
        },
        indexes = {
                @Index(name = "idx_vehicule_proprietaire", columnList = "proprietaire_id"),
                @Index(name = "idx_vehicule_current_fleet", columnList = "current_fleet_id"),
                @Index(name = "idx_vehicule_statut", columnList = "statut")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicule extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optionnelle (2026-09-13) : une moto neuve ou en attente d'immatriculation officielle doit
     * pouvoir etre enregistree quand meme, via numeroChassis comme identifiant. La contrainte unique
     * en base tolere plusieurs NULL (MySQL/MariaDB) - seules les valeurs non-nulles sont dedupliquees.
     * Le NOT NULL d'origine ne se relache pas tout seul via ddl-auto=update : voir
     * sql/manual/2026-09-13_vehicule_immatriculation_nullable.sql. */
    @Size(max = 20)
    @Column(name = "immatriculation", unique = true, length = 20)
    private String immatriculation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private VehiculeType type;

    @NotBlank
    @Size(max = 60)
    @Column(name = "marque", nullable = false, length = 60)
    private String marque;

    @NotBlank
    @Size(max = 60)
    @Column(name = "modele", nullable = false, length = 60)
    private String modele;

    @Size(max = 50)
    @Column(name = "numero_chassis", length = 50)
    private String numeroChassis;

    @Column(name = "date_mise_en_circulation")
    private LocalDate dateMiseEnCirculation;

    @Column(name = "puissance_fiscale")
    private Integer puissanceFiscale;

    /** Uniquement pertinent pour VehiculeType.MOTO, mais champ non contraint par type au niveau
     * entite (cf. puissanceFiscale qui n'est pas non plus reserve a AUTO) - nullable, jamais valide
     * comme obligatoire quel que soit le type. */
    @Column(name = "cylindree")
    private Integer cylindree;

    @Size(max = 30)
    @Column(name = "couleur", length = 30)
    private String couleur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proprietaire_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vehicule_proprietaire"))
    private Insured proprietaire;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 20)
    private VehiculeStatut statut = VehiculeStatut.ACTIF;

    /** Cache de lecture uniquement (evite une jointure pour "dans quelle flotte est ce vehicule") -
     * toujours ecrit dans la meme transaction que le changement de statut FleetVehicle qu'il reflete. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_fleet_id", foreignKey = @ForeignKey(name = "fk_vehicule_current_fleet"))
    private Fleet currentFleet;
}
