package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Paiement d'une prime de transport ou d'une indemnite de communication d'un commercial pour un mois
 * comptable (2026-11-01). Un enregistrement par (commercial, mois, type), cree au premier essai de
 * paiement ; le montant est fige (snapshot de la fiche commercial) a chaque tentative non encore payee.
 * Independant du bonus (CommercialPerformance) et de l'eligibilite a celui-ci.
 * paidAt non null = avantage paye (virement Wave confirme ou enregistrement manuel).
 */
@Entity
@Table(
        name = "t_assur_zen_commercial_benefit_payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_commercial_benefit_payment",
                columnNames = {"commercial_id", "mois_comptable_id", "type"}),
        indexes = @Index(name = "idx_commercial_benefit_payment_mois", columnList = "mois_comptable_id")
)
@Getter
@Setter
@NoArgsConstructor
public class CommercialBenefitPayment extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_benefit_payment_commercial"))
    private Commercial commercial;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mois_comptable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_benefit_payment_mois"))
    private MoisComptable moisComptable;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeAvantageCommercial type;

    /** Montant fige (FCFA) au moment du paiement. */
    @NotNull
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id", foreignKey = @ForeignKey(name = "fk_commercial_benefit_payment_paid_by"))
    private User paidBy;

    /** VIREMENT_SYCAPAY ou ENREGISTREMENT_MANUEL. */
    @Column(name = "mode_paiement", length = 30)
    private String modePaiement;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** Dernier virement SycaPay tente (SycaPayTransaction.id), quel que soit son statut. */
    @Column(name = "sycapay_transaction_id")
    private Long sycapayTransactionId;
}
