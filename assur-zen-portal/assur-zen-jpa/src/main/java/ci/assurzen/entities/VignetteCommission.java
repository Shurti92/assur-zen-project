package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Commission forfaitaire due a l'apporteur (partenaire OU commercial interne, jamais les deux) d'une
 * demande de vignette moto - contrat "commission apporteur vignette", 2026-09-15. Une ligne au plus
 * par demande (creee immediatement a DemandeVignetteMotoService.creer() si un apporteur est renseigne,
 * montant fige au moment de la creation - meme discipline que le tarif de la demande elle-meme, jamais
 * recalcule apres coup meme si la configuration change ensuite). Registre dedie et independant de
 * CommissionPayment/CommercialCommissionPayment (qui restent scopes aux Contract) : une seule entite
 * porte ici a la fois l'etat "a payer" et "paye", contrairement au couple Contract+Payment cote
 * contrats, car une demande de vignette n'a jamais qu'une seule commission a verser.
 */
@Entity
@Table(
        name = "t_assur_zen_vignette_commission",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vignette_commission_demande", columnNames = "demande_vignette_moto_id")
        },
        indexes = {
                @Index(name = "idx_vignette_commission_partenaire", columnList = "partenaire_id"),
                @Index(name = "idx_vignette_commission_commercial", columnList = "commercial_id"),
                @Index(name = "idx_vignette_commission_statut", columnList = "statut")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VignetteCommission implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_vignette_moto_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_vignette_commission_demande"))
    private DemandeVignetteMoto demandeVignetteMoto;

    /** Exactement un des deux (partenaire/commercial) est renseigne - valide a la creation par
     * VignetteCommissionService, jamais au niveau base (les deux colonnes restent nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", foreignKey = @ForeignKey(name = "fk_vignette_commission_partenaire"))
    private Partenaire partenaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", foreignKey = @ForeignKey(name = "fk_vignette_commission_commercial"))
    private Commercial commercial;

    @NotNull
    @Column(name = "montant", nullable = false)
    private Long montant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 20)
    private CommissionStatus statut = CommissionStatus.pending;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id", foreignKey = @ForeignKey(name = "fk_vignette_commission_paid_by"))
    private User paidBy;

    @Size(max = 255)
    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
