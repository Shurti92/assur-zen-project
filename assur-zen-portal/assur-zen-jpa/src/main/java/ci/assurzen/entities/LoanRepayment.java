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
 * Remboursement enregistre sur un pret. Jamais modifie apres creation (append-only, meme choix que
 * FleetMovement/GedDocumentVersion). Ne porte pas de FK directe vers une echeance : la repartition
 * sur une ou plusieurs echeances est enregistree separement dans LoanRepaymentAllocation (§19 du
 * cahier des charges - un remboursement peut couvrir plusieurs echeances, partiellement).
 */
@Entity
@Table(
        name = "t_assur_zen_loan_repayments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loan_repayment_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_loan_repayment_loan", columnList = "loan_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepayment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_repayment_loan"))
    private Loan loan;

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
    @Column(name = "payment_method", nullable = false, length = 20)
    private DisbursementMethod paymentMethod;

    @Size(max = 100)
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @NotNull
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Size(max = 100)
    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Size(max = 500)
    @Column(name = "comment", length = 500)
    private String comment;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
