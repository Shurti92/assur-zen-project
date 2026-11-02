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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Demande generique de sortie de fonds (moteur financier transversal). Le beneficiaire est
 * generique (entity_type/entity_id, meme patron que GedDocumentLink) plutot que des FK figees
 * partner_id/client_id/supplier_id : seul PARTNER est resolu/valide contre une entite reelle
 * (Partenaire) dans cette phase, les autres types restent opaques (aucune entite Supplier/Employee
 * n'existe dans ce projet). Un Loan (entities/Loan.java) est TOUJOURS l'extension d'une instance de
 * cette classe avec outflowType.code == "LOAN".
 */
@Entity
@Table(
        name = "t_assur_zen_fund_outflows",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fund_outflow_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_fund_outflow_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_fund_outflow_type", columnList = "outflow_type_id"),
                @Index(name = "idx_fund_outflow_status", columnList = "status"),
                @Index(name = "idx_fund_outflow_beneficiary", columnList = "beneficiary_type, beneficiary_id"),
                @Index(name = "idx_fund_outflow_created_at", columnList = "created_at"),
                @Index(name = "idx_fund_outflow_due_date", columnList = "due_date")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundOutflow extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Builder.Default
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    private String uuid = UUID.randomUUID().toString();

    @NotBlank
    @Size(max = 20)
    @Column(name = "reference", nullable = false, unique = true, length = 20)
    private String reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outflow_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fund_outflow_type"))
    private FundOutflowType outflowType;

    @Size(max = 30)
    @Column(name = "beneficiary_type", length = 30)
    private String beneficiaryType;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Size(max = 150)
    @Column(name = "beneficiary_label", length = 150)
    private String beneficiaryLabel;

    @NotNull
    @Positive
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotBlank
    @Builder.Default
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "XOF";

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private FundOutflowStatus status = FundOutflowStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "priority", nullable = false, length = 10)
    private FundOutflowPriority priority = FundOutflowPriority.NORMAL;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Size(max = 100)
    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
    @Size(max = 100)
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "authorized_at")
    private Instant authorizedAt;
    @Size(max = 100)
    @Column(name = "authorized_by", length = 100)
    private String authorizedBy;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;
    @Size(max = 100)
    @Column(name = "disbursed_by", length = 100)
    private String disbursedBy;

    @Column(name = "closed_at")
    private Instant closedAt;
    @Size(max = 100)
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    // Conditions du pret, renseignees des la creation quand outflowType.code == "LOAN" (figees avant
    // approbation, pas inventees au decaissement) - copiees dans Loan par LoanService au moment ou
    // disburse() cree effectivement le pret. Sans effet/ignorees pour les autres types de sortie.
    @Column(name = "loan_interest_rate", precision = 5, scale = 2)
    private BigDecimal loanInterestRate;
    @Column(name = "loan_start_date")
    private LocalDate loanStartDate;
    @Column(name = "loan_expected_end_date")
    private LocalDate loanExpectedEndDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "loan_repayment_frequency", length = 20)
    private RepaymentFrequency loanRepaymentFrequency;
    @Column(name = "loan_number_of_installments")
    private Integer loanNumberOfInstallments;
}
