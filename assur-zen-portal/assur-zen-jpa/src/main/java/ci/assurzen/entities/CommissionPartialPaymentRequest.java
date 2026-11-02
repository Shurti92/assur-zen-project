package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Demande de versement anticipe d'une partie du solde de commissions en attente d'un partenaire
 * (contrat "Espace Partenaire" §2, 2026-08-21). Detachee du niveau contrat expres : contrairement a
 * CommissionPayment (1 paiement total max par contrat), cette demande porte sur le solde AGREGE du
 * partenaire (somme de Contract.partnerShare des contrats signed/pending), pas sur un contrat precis.
 *
 * Depuis le 2026-09-25, porte aussi les demandes des COMMERCIAUX internes (commercial renseigne,
 * partenaire NULL) : meme table pour que la validation admin (POST /commissions/partial-payment-requests
 * /{id}/valider) serve les deux. Exactement l'un des deux (partenaire / commercial) est renseigne.
 */
@Entity
@Table(
        name = "t_assur_zen_commission_partial_payment_request",
        indexes = {
                @Index(name = "idx_cppr_partenaire", columnList = "partenaire_id"),
                @Index(name = "idx_cppr_commercial", columnList = "commercial_id"),
                @Index(name = "idx_cppr_requested_at", columnList = "requested_at"),
                @Index(name = "idx_cppr_status", columnList = "status")
        },
        // NULL n'est jamais egal a NULL en SQL : cette contrainte n'empeche pas plusieurs demandes
        // sans idempotency_key (header absent), seulement deux demandes du MEME partenaire avec la
        // MEME cle non-nulle.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cppr_partenaire_idempotency_key",
                        columnNames = {"partenaire_id", "idempotency_key"}),
                @UniqueConstraint(name = "uk_cppr_commercial_idempotency_key",
                        columnNames = {"commercial_id", "idempotency_key"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionPartialPaymentRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL pour une demande de commercial (2026-09-25) - colonne rendue nullable par
     * sql/manual/2026-09-25_commercial_demande_paiement_commission.sql. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", foreignKey = @ForeignKey(name = "fk_cppr_partenaire"))
    private Partenaire partenaire;

    /** Commercial interne demandeur (2026-09-25) - NULL pour une demande de partenaire. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", foreignKey = @ForeignKey(name = "fk_cppr_commercial"))
    private Commercial commercial;

    /** TOTAL ou PARTIAL - renseigne pour les demandes de commercial uniquement (NULL = demande
     * partenaire, toujours partielle). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10)
    private PaymentRequestType type;

    @NotNull
    @Column(name = "requested_amount", nullable = false)
    private Long requestedAmount;

    /** Solde net (deja diminue des demandes precedentes) juste avant cette demande - trace/audit. */
    @NotNull
    @Column(name = "pending_balance_before", nullable = false)
    private Long pendingBalanceBefore;

    @NotNull
    @Column(name = "pending_balance_after", nullable = false)
    private Long pendingBalanceAfter;

    /** Snapshot de Partenaire.numeroWaveCommission au moment de LA CREATION de la demande (pas de la
     * validation) - fige la destination du versement des cet instant : si le numero est modifie sur
     * la fiche partenaire APRES la soumission de cette demande, le versement reste dirige vers le
     * numero d'origine, jamais vers un numero substitue apres coup. Cible de fraude la plus sensible
     * du module (audit de securite pre-production, 2026-08-23). Null si le partenaire n'avait aucun
     * numero renseigne au moment de la demande. Demande de commercial : son telephone (Profile.phone),
     * canal de versement decide le 2026-09-25, fige de la meme facon. */
    @Column(name = "numero_wave_commission_snapshot", length = 20)
    private String numeroWaveCommissionSnapshot;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartialPaymentRequestStatus status = PartialPaymentRequestStatus.pending;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    /** Renseigne uniquement une fois validee (statut paid) - admin/finance ayant approuve le versement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id", foreignKey = @ForeignKey(name = "fk_cppr_validated_by"))
    private User validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    /** Cle d'idempotence fournie par le client (UUID v4, header "Idempotency-Key") sur la CREATION
     * uniquement - un rejeu avec la meme cle pour le meme partenaire renvoie cette demande existante au
     * lieu d'en creer une seconde. Nullable : header recommande, pas encore obligatoire (accord avec
     * l'equipe frontend/securite du 2026-08-23) - une demande sans cle retombe sur l'ancienne
     * deduplication par fenetre de 10s. Pas de TTL/purge : conservee indefiniment, comme la demande
     * elle-meme (audit finance). */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Part de cette avance (demande PAYEE) deja imputee sur des contrats payes ensuite par l'admin
     * (2026-09-26) - l'avance restante (requestedAmount - montantImpute) reste a deduire des prochains
     * paiements par contrat. NULL = 0 (demandes anterieures, demandes des commerciaux). */
    @Column(name = "montant_impute")
    private Long montantImpute;

    /** Avance ecrite au grand livre a son paiement (2026-09-26). NULL/false pour les avances payees avant
     * ce changement : leur montant n'est alors comptabilise qu'avec les contrats sur lesquels elles sont
     * imputees. */
    @Column(name = "comptabilisee")
    private Boolean comptabilisee;

    public boolean estComptabilisee() {
        return Boolean.TRUE.equals(comptabilisee);
    }

    /** Avance encore disponible : 0 si la demande n'est pas payee. */
    public long avanceRestante() {
        if (status != PartialPaymentRequestStatus.paid || requestedAmount == null) {
            return 0L;
        }
        return Math.max(0L, requestedAmount - (montantImpute == null ? 0L : montantImpute));
    }
}
