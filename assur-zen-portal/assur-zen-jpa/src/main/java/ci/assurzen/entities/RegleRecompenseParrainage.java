package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Regle de recompense configurable rattachee a un programme : "le [beneficiaire] recoit [type]
 * (montant fixe / pourcentage / points)". Plusieurs regles actives peuvent coexister sur un meme
 * programme (ex: une pour PARRAIN, une pour FILLEUL) ; RecompenseParrainageService les applique par
 * ordre de priorite lors de la validation d'un parrainage.
 */
@Entity
@Table(
        name = "t_assur_zen_regle_recompense_parrainage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_regle_recompense_parrainage_programme_nom", columnNames = {"programme_id", "nom"})
        },
        indexes = {
                @Index(name = "idx_regle_recompense_parrainage_programme", columnList = "programme_id"),
                @Index(name = "idx_regle_recompense_parrainage_actif", columnList = "actif")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegleRecompenseParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programme_id", nullable = false, foreignKey = @ForeignKey(name = "fk_regle_recompense_parrainage_programme"))
    private ProgrammeParrainage programme;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_recompense", nullable = false, length = 20)
    private TypeRecompenseParrainage typeRecompense;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiaire", nullable = false, length = 10)
    private BeneficiaireParrainage beneficiaire;

    @Column(name = "montant_fixe")
    private Long montantFixe;

    @Column(name = "pourcentage")
    private Double pourcentage;

    @Column(name = "points")
    private Integer points;

    /** Prime annuelle minimale du contrat pour que cette regle s'applique ; null = pas de seuil propre a la regle. */
    @Column(name = "prime_minimum")
    private Long primeMinimum;

    /** Plafond de la recompense calculee (utile surtout pour le pourcentage) ; null = pas de plafond. */
    @Column(name = "plafond_recompense")
    private Long plafondRecompense;

    @NotNull
    @Builder.Default
    @Column(name = "priorite", nullable = false)
    private Integer priorite = 0;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
