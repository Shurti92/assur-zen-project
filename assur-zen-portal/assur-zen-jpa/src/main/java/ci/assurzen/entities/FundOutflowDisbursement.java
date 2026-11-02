package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Mouvement financier reel d'une sortie de fonds. Jamais modifie apres creation (meme choix que
 * GedDocumentVersion/FleetMovement - historique append-only). idempotencyKey + contrainte unique
 * (fund_outflow_id, idempotency_key) : patron identique a CommissionPartialPaymentRequest
 * (module Espace Partenaire, 2026-08-21/23).
 */
@Entity
@Table(
        name = "t_assur_zen_fund_outflow_disbursements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fod_reference", columnNames = "reference"),
                @UniqueConstraint(name = "uk_fod_idempotency_key", columnNames = {"fund_outflow_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_fod_fund_outflow", columnList = "fund_outflow_id"),
                @Index(name = "idx_fod_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundOutflowDisbursement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_outflow_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fod_fund_outflow"))
    private FundOutflow fundOutflow;

    @NotBlank
    @Size(max = 20)
    @Column(name = "reference", nullable = false, unique = true, length = 20)
    private String reference;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotBlank
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private DisbursementMethod method;

    @Size(max = 100)
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private DisbursementStatus status = DisbursementStatus.PENDING;

    @Size(max = 64)
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Size(max = 100)
    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
