package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_commission_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commission_payment_contract", columnNames = "contract_id")
        },
        indexes = {
                @Index(name = "idx_commission_payment_partenaire", columnList = "partenaire_id"),
                @Index(name = "idx_commission_payment_paid_at", columnList = "paid_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionPayment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commission_payment_contract"))
    private Contract contract;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commission_payment_partenaire"))
    private Partenaire partenaire;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Admin ayant declenche le paiement manuel - NULL pour un paiement automatique SycaPay
     * (automatic = true, aucun utilisateur a l'origine). Nullable depuis le 2026-09-23 (cf.
     * sql/manual/2026-09-23_sycapay_partenaire_beneficiaire.sql). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id", foreignKey = @ForeignKey(name = "fk_commission_payment_paid_by"))
    private User paidBy;

    /** true = verse automatiquement par virement Wave SycaPay (partenaire en frequence IMMEDIAT,
     * 2026-09-23) ; false = paiement enregistre manuellement par un admin (POST /commissions/pay). */
    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "automatic", nullable = false)
    private boolean automatic = false;

    /** Id technique de la SycaPayTransaction a l'origine d'un paiement automatique (pas de FK : meme
     * convention que SycaPayTransaction.contractId, cf. sa Javadoc). NULL pour un paiement manuel. */
    @Column(name = "sycapay_transaction_id")
    private Long sycapayTransactionId;

    @Size(max = 255)
    @Column(name = "reference", length = 255)
    private String reference;

    @CreationTimestamp
    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    /** Resultat de l'envoi du SMS de confirmation de CE paiement (2026-09-24) : true envoye, false echec,
     * NULL si aucun envoi trace (paiements anterieurs a cette colonne). Renseigne au moment de l'envoi,
     * expose tel quel par l'historique (notificationEnvoyee). */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;

    /** Part de amount deja versee au partenaire par des demandes de paiement partiel (avances), imputee
     * sur ce contrat au moment du paiement (2026-09-26). Montant reellement verse = amount - avanceImputee.
     * NULL pour les paiements anterieurs (= 0). */
    @Column(name = "avance_imputee")
    private Long avanceImputee;

    /** Montant reellement verse ou enregistre pour ce paiement (hors avance deja versee). */
    public long montantVerse() {
        return (amount == null ? 0L : amount) - (avanceImputee == null ? 0L : avanceImputee);
    }
}
