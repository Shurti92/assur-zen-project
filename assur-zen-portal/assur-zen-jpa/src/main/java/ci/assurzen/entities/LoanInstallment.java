package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ligne d'echeancier d'un pret. Mutable au fil des remboursements (status/paidAt) mais jamais
 * individuellement supprimable - pas d'AuditableEntity/soft-delete, juste createdAt/updatedAt.
 * paidAmount n'est PAS stocke ici : toujours recalcule par requete SUM sur LoanRepaymentAllocation
 * (meme discipline que les compteurs Flotte/Prime Objectif - jamais incremente en memoire).
 */
@Entity
@Table(
        name = "t_assur_zen_loan_installments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loan_installment_number", columnNames = {"loan_id", "installment_number"})
        },
        indexes = {
                @Index(name = "idx_loan_installment_loan", columnList = "loan_id"),
                @Index(name = "idx_loan_installment_status", columnList = "status"),
                @Index(name = "idx_loan_installment_due_date", columnList = "due_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanInstallment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_installment_loan"))
    private Loan loan;

    @NotNull
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull
    @Column(name = "principal_amount", nullable = false)
    private Long principalAmount;

    @NotNull
    @Column(name = "interest_amount", nullable = false)
    private Long interestAmount;

    @NotNull
    @Column(name = "total_due", nullable = false)
    private Long totalDue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
