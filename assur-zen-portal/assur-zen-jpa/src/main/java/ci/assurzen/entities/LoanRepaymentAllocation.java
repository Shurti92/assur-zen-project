package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

/**
 * Repartition d'un LoanRepayment sur une LoanInstallment. Ajout au-dela du schema litteral du
 * cahier des charges : un FK installment_id unique et nullable sur loan_repayment ne peut pas
 * representer une repartition sur plusieurs echeances (§19). Generee automatiquement par
 * LoanRepaymentService (allocation FIFO sur les echeances impayees les plus anciennes), jamais
 * saisie manuellement.
 */
@Entity
@Table(
        name = "t_assur_zen_loan_repayment_allocations",
        indexes = {
                @Index(name = "idx_lra_repayment", columnList = "repayment_id"),
                @Index(name = "idx_lra_installment", columnList = "installment_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepaymentAllocation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repayment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lra_repayment"))
    private LoanRepayment repayment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lra_installment"))
    private LoanInstallment installment;

    @NotNull
    @Column(name = "allocated_amount", nullable = false)
    private Long allocatedAmount;
}
