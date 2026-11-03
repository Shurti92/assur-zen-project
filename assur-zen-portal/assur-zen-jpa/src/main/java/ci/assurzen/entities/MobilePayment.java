package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tentative de paiement initiee depuis l'application mobile (2026-11-02). Pour l'instant SIMULEE en dev (aucun
 * fournisseur d'encaissement n'est branche) : simulated = true, aucun argent ne bouge. Le statut CONFIRMED ne
 * vient jamais du client : il est pose par le serveur (webhook du fournisseur, ou simulation).
 */
@Entity
@Table(
        name = "t_assur_zen_mobile_payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mobile_payment_reference", columnNames = "reference"),
                @UniqueConstraint(name = "uk_mobile_payment_idempotency", columnNames = {"user_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_mobile_payment_user", columnList = "user_id"),
                @Index(name = "idx_mobile_payment_payable", columnList = "payable_reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MobilePayment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "payable_reference", nullable = false, length = 60)
    private String payableReference;

    @Column(name = "method_id", nullable = false, length = 20)
    private String methodId;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "amount", nullable = false)
    private Long amount;

    /** PENDING, CONFIRMED ou FAILED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Contrat de renouvellement cree apres confirmation (null tant que non confirme). */
    @Column(name = "renewal_contract_id")
    private Long renewalContractId;
}
