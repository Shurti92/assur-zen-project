package ci.assurzen.sycapay.entity;

import ci.assurzen.entities.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Une ligne par contrat (commission commerciale) ayant fait l'objet d'une tentative de paiement
 * SycaPay - UNIQUE(contract_id) : une nouvelle tentative apres FAILED reactive la meme ligne
 * (nouvelle transaction_reference, attempt_count incremente) plutot que d'en creer une autre, pour
 * garder un historique unique et centralise par contrat (cf. SycaPayTransactionStatus pour les
 * transitions autorisees).
 *
 * Reference volontairement contractId/commercialId en Long bruts plutot qu'en relations JPA
 * (@ManyToOne) : cette entite traverse plusieurs transactions Spring courtes et independantes
 * (creation PENDING committee avant l'appel HTTP sortant, puis finalisation dans une transaction
 * separee - cf. SycaPayTransactionWriter) et ne doit jamais porter d'entite potentiellement detachee
 * d'une transaction precedente.
 */
@Entity
@Table(
        name = "t_assur_zen_sycapay_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sycapay_transaction_reference", columnNames = "transaction_reference"),
                @UniqueConstraint(name = "uk_sycapay_transaction_contract", columnNames = "contract_id"),
                @UniqueConstraint(name = "uk_sycapay_transaction_partial_request", columnNames = "partial_payment_request_id"),
                @UniqueConstraint(name = "uk_sycapay_transaction_commercial_perf", columnNames = "commercial_performance_id"),
                @UniqueConstraint(name = "uk_sycapay_transaction_benefit_payment", columnNames = "commercial_benefit_payment_id")
        },
        indexes = {
                @Index(name = "idx_sycapay_transaction_commercial", columnList = "commercial_id"),
                @Index(name = "idx_sycapay_transaction_partenaire", columnList = "partenaire_id"),
                @Index(name = "idx_sycapay_transaction_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SycaPayTransaction extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "transaction_reference", nullable = false, length = 40)
    private String transactionReference;

    /** Contrat paye - NULL pour le virement d'une demande de paiement partiel / anticipe (2026-09-26), qui
     * porte alors partialPaymentRequestId. Exactement l'un des deux est renseigne. Colonne rendue nullable
     * par sql/manual/2026-09-26_sycapay_virement_demandes_paiement.sql. */
    @Column(name = "contract_id")
    private Long contractId;

    /** Demande de paiement (avance partenaire ou demande de commercial) payee par ce virement (2026-09-26)
     * - UNIQUE : une transaction par demande, meme garde d'idempotence que par contrat. */
    @Column(name = "partial_payment_request_id")
    private Long partialPaymentRequestId;

    /** Bonus mensuel d'un commercial (CommercialPerformance) paye par ce virement (2026-10-02) - contractId
     * est alors null. UNIQUE : une transaction par bonus, meme garde d'idempotence que par contrat. */
    @Column(name = "commercial_performance_id")
    private Long commercialPerformanceId;

    /** Prime de transport / indemnite de communication d'un commercial (CommercialBenefitPayment) payee par ce
     * virement (2026-11-01). UNIQUE : une transaction par avantage et par mois. */
    @Column(name = "commercial_benefit_payment_id")
    private Long commercialBenefitPaymentId;

    /** Nullable depuis l'ouverture aux partenaires (2026-09-23) : renseigne uniquement pour
     * beneficiaryType = COMMERCIAL (cf. sql/manual/2026-09-23_sycapay_partenaire_beneficiaire.sql pour
     * relacher la contrainte NOT NULL existante, qu'Hibernate ne relache jamais tout seul). */
    @Column(name = "commercial_id")
    private Long commercialId;

    /** Renseigne uniquement pour beneficiaryType = PARTENAIRE. */
    @Column(name = "partenaire_id")
    private Long partenaireId;

    /** Colonne nullable en base (lignes anterieures au 2026-09-23 : NULL = COMMERCIAL, cf.
     * getBeneficiaryType()) ; toujours renseignee pour toute nouvelle transaction. */
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", length = 20)
    private SycaPayBeneficiaryType beneficiaryType = SycaPayBeneficiaryType.COMMERCIAL;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotBlank
    @Size(max = 10)
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Size(max = 255)
    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Size(max = 30)
    @Column(name = "beneficiary_phone", length = 30)
    private String beneficiaryPhone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SycaPayTransactionStatus status;

    /** = referencetransfer retourne par checktransferwave. */
    @Size(max = 100)
    @Column(name = "sycapay_reference", length = 100)
    private String sycapayReference;

    /** = transactionid, uniquement connu apres reconciliation via getStatusByOrderID (absent de la
     * reponse immediate de checktransferwave). */
    @Size(max = 100)
    @Column(name = "sycapay_transaction_id", length = 100)
    private String sycapayTransactionId;

    @Column(name = "sycapay_response_code")
    private Integer sycapayResponseCode;

    @Size(max = 500)
    @Column(name = "sycapay_response_message", length = 500)
    private String sycapayResponseMessage;

    @NotNull
    @Column(name = "request_date", nullable = false)
    private Instant requestDate;

    @Column(name = "processing_date")
    private Instant processingDate;

    @Column(name = "completed_date")
    private Instant completedDate;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Size(max = 50)
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Size(max = 500)
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** true = virement redirige par le MODE TEST (hors production, SycaPayModeTest) : amount et
     * beneficiaryPhone sont alors le montant et le numero REELLEMENT envoyes a SycaPay (test), pas ceux
     * de la commission. */
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "test_mode", nullable = false)
    private boolean testMode = false;

    /** Transactions creees avant le 2026-09-23 (colonne NULL) : toutes commerciales. */
    public SycaPayBeneficiaryType getBeneficiaryType() {
        return beneficiaryType == null ? SycaPayBeneficiaryType.COMMERCIAL : beneficiaryType;
    }
}
