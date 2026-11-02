package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Objectif GLOBAL d'un commercial pour un exercice comptable (contrat "Module Commercial",
 * 2026-09-07) - independant des objectifs mensuels (CommercialMonthlyObjective) : la somme des
 * objectifs mensuels n'est jamais forcee a egaler celle-ci (cf. CommercialObjectiveService.simuler
 * pour l'alerte d'ecart, purement informative).
 */
@Entity
@Table(
        name = "t_assur_zen_commercial_objective",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_objective", columnNames = {"commercial_id", "exercice_comptable_id", "objective_type"})
        },
        indexes = {
                @Index(name = "idx_commercial_objective_commercial", columnList = "commercial_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommercialObjective implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_objective_commercial"))
    private Commercial commercial;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_comptable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_objective_exercice"))
    private ExerciceComptable exerciceComptable;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "objective_type", nullable = false, length = 20)
    private CommercialObjectiveType objectiveType;

    @NotNull
    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "target_count")
    private Integer targetCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    private Long version;
}
