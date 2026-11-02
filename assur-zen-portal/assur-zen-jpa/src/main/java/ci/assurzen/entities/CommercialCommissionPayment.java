package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/** Paiement d'une commission commerciale par contrat - mirroir de CommissionPayment (partenaire),
 * flux de paiement separe (decision produit du 2026-09-08), pas fusionne au cycle mensuel bonus. */
@Entity
@Table(
        name = "t_assur_zen_commercial_commission_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_commission_payment_contract", columnNames = "contract_id")
        },
        indexes = {
                @Index(name = "idx_commercial_commission_payment_commercial", columnList = "commercial_id"),
                @Index(name = "idx_commercial_commission_payment_paid_at", columnList = "paid_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialCommissionPayment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_commission_payment_contract"))
    private Contract contract;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_commission_payment_commercial"))
    private Commercial commercial;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commercial_commission_payment_paid_by"))
    private User paidBy;

    @Size(max = 255)
    @Column(name = "reference", length = 255)
    private String reference;

    @CreationTimestamp
    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    /** Id technique de la SycaPayTransaction dont le virement a produit ce paiement (2026-09-23) - pas
     * de FK, meme convention que SycaPayTransaction.contractId. Sert a l'idempotence de l'enregistrement
     * apres reconciliation et a l'historique. NULL pour les paiements anterieurs. */
    @Column(name = "sycapay_transaction_id")
    private Long sycapayTransactionId;

    /** Resultat de l'envoi du SMS de confirmation de CE paiement (2026-09-24) : true envoye, false echec,
     * NULL si aucun envoi trace (paiements anterieurs a cette colonne). Renseigne au moment de l'envoi,
     * expose tel quel par l'historique (notificationEnvoyee). */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;
}
