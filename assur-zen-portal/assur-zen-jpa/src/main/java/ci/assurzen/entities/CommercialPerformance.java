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
 * Performance mensuelle d'un commercial - fusionne volontairement performance + bonus + snapshot de
 * remuneration en une seule entite (decision produit validee le 2026-09-07, evite 3 tables
 * synchronisees pour un besoin 1:1 par (commercial, moisComptable) ; cf. PrimeObjectif pour le meme
 * choix de snapshot fige). Toutes les valeurs sont figees au moment du calcul (objectif/realise/taux/
 * bonus_rate/remuneration) : elles ne bougent jamais retroactivement si le commercial, ses objectifs ou
 * ses contrats sont modifies plus tard - seul un recalcul explicite avant VALIDATED les met a jour
 * (cf. CommercialPerformanceService.recalculer). Le bonus est toujours calcule sur l'OBJECTIF, jamais
 * sur le realise (regle confirmee le 2026-09-07).
 */
@Entity
@Table(
        name = "t_assur_zen_commercial_performance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_performance", columnNames = {"commercial_id", "mois_comptable_id"})
        },
        indexes = {
                @Index(name = "idx_commercial_performance_commercial", columnList = "commercial_id"),
                @Index(name = "idx_commercial_performance_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommercialPerformance implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_performance_commercial"))
    private Commercial commercial;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mois_comptable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_performance_mois"))
    private MoisComptable moisComptable;

    /** Snapshot de CommercialMonthlyObjective.targetAmount au moment du calcul (0 si aucun objectif defini ce mois). */
    @NotNull
    @Column(name = "objective_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal objectiveAmount;

    /** Snapshot de CommercialProductionService.computeRealise() au moment du calcul. */
    @NotNull
    @Column(name = "realized_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal realizedAmount;

    @NotNull
    @Column(name = "achievement_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal achievementRate;

    /** Snapshot de COMMERCIAL_BONUS_RATE au moment du calcul - garantit un historique correct meme si le taux global change plus tard. */
    @NotNull
    @Column(name = "bonus_rate_snapshot", nullable = false, precision = 7, scale = 4)
    private BigDecimal bonusRateSnapshot;

    @NotNull
    @Builder.Default
    @Column(name = "bonus_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @NotNull
    @Builder.Default
    @Column(name = "eligible", nullable = false)
    private boolean eligible = false;

    /** Snapshots de Commercial.baseSalary/transportBenefitAmount/phoneAllowanceAmount au moment du calcul (§46/§47). */
    @NotNull
    @Column(name = "base_salary_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalarySnapshot;

    @NotNull
    @Column(name = "transport_benefit_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal transportBenefitSnapshot;

    @NotNull
    @Column(name = "phone_allowance_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal phoneAllowanceSnapshot;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutCommercialPerformance statut = StatutCommercialPerformance.OPEN;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id", foreignKey = @ForeignKey(name = "fk_commercial_performance_validated_by"))
    private User validatedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id", foreignKey = @ForeignKey(name = "fk_commercial_performance_paid_by"))
    private User paidBy;

    /** Non-null uniquement sur la transition vers PAID - meme convention que PrimeObjectif.notificationEnvoyee. */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;

    /** Virement SycaPay du bonus (SycaPayTransaction.id, 2026-10-02) - dernier virement tente, quel que
     * soit son statut ; null si aucun virement n'a ete tente ou si le bonus a ete paye hors application. */
    @Column(name = "sycapay_transaction_id")
    private Long sycapayTransactionId;

    /** VIREMENT_SYCAPAY ou ENREGISTREMENT_MANUEL (memes valeurs que les commissions) - pose au paiement. */
    @Column(name = "mode_paiement", length = 30)
    private String modePaiement;

    /** Reference libre saisie par l'admin au paiement (virement hors application, piece comptable...). */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
