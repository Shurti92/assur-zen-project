package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Extension specialisee d'un FundOutflow (outflowType.code == "LOAN") pour un pret partenaire.
 * N'a PAS de champ status propre : delegue systematiquement a fundOutflow.getStatus() - une seule
 * source de verite, pas de machine a etats parallele qui peut deriver (meme philosophie que les
 * compteurs Flotte recalcules par requete plutot que dupliques).
 */
@Entity
@Table(
        name = "t_assur_zen_loans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loan_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_loan_reference", columnNames = "loan_reference"),
                @UniqueConstraint(name = "uk_loan_fund_outflow", columnNames = "fund_outflow_id")
        },
        indexes = {
                @Index(name = "idx_loan_partner", columnList = "partner_id"),
                @Index(name = "idx_loan_expected_end_date", columnList = "expected_end_date")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Builder.Default
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    private String uuid = UUID.randomUUID().toString();

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_outflow_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_fund_outflow"))
    private FundOutflow fundOutflow;

    @NotBlank
    @Size(max = 20)
    @Column(name = "loan_reference", nullable = false, unique = true, length = 20)
    private String loanReference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_partner"))
    private Partenaire partner;

    /** Snapshot de Partenaire.numeroWaveCommission au moment de la creation du pret - meme garde-fou
     * anti-fraude que CommissionPartialPaymentRequest.numeroWaveCommissionSnapshot : si le numero est
     * modifie sur la fiche partenaire APRES, le versement reste dirige vers le numero d'origine. */
    @Size(max = 20)
    @Column(name = "partner_payment_destination_snapshot", length = 20)
    private String partnerPaymentDestinationSnapshot;

    @NotNull
    @Positive
    @Column(name = "principal_amount", nullable = false)
    private Long principalAmount;

    @NotNull
    @Builder.Default
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @NotBlank
    @Builder.Default
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "XOF";

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "expected_end_date", nullable = false)
    private LocalDate expectedEndDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    private RepaymentFrequency repaymentFrequency;

    @NotNull
    @Positive
    @Column(name = "number_of_installments", nullable = false)
    private Integer numberOfInstallments;
}
