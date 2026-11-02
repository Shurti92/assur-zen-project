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
 * Objectif MENSUEL d'un commercial, rattache a un MoisComptable existant (pas de colonnes year/month
 * brutes - contrat "Module Commercial", 2026-09-07). Une seule ligne active par
 * (commercial, moisComptable, objectiveType) - UNIQUE ci-dessous. Toute revision modifie cette meme
 * ligne (motif obligatoire) plutot que d'en creer une nouvelle : l'historique n'est pas stocke ici,
 * il est traite par le moteur d'audit fonctionnel existant (FunctionalAuditRecorder.recordUpdate,
 * expose via /audit-logs) - cf. CommercialMonthlyObjectiveService.reviser.
 */
@Entity
@Table(
        name = "t_assur_zen_commercial_monthly_objective",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_monthly_objective", columnNames = {"commercial_id", "mois_comptable_id", "objective_type"})
        },
        indexes = {
                @Index(name = "idx_commercial_monthly_objective_commercial", columnList = "commercial_id"),
                @Index(name = "idx_commercial_monthly_objective_mois", columnList = "mois_comptable_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommercialMonthlyObjective implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_monthly_objective_commercial"))
    private Commercial commercial;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mois_comptable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_monthly_objective_mois"))
    private MoisComptable moisComptable;

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
