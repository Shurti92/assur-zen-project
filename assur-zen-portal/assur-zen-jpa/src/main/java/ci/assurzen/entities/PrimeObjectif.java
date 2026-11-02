package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Prime versee a un partenaire ayant atteint/depasse son objectif de production annuel sur un
 * exercice comptable donne (contrat "Prime d'objectif partenaire", 2026-08-28). Une seule demande par
 * (partenaire, exercice) - cf. contrainte unique. Les valeurs objectif/realise/taux/montant sont figees
 * au moment de la demande (snapshot) : elles ne doivent pas bouger retroactivement si le partenaire ou
 * ses contrats sont modifies plus tard.
 */
@Entity
@Table(
        name = "t_assur_zen_prime_objectif",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_prime_objectif_partenaire_exercice", columnNames = {"partenaire_id", "exercice_comptable_id"}),
                @UniqueConstraint(name = "uk_prime_objectif_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_prime_objectif_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrimeObjectif implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prime_objectif_partenaire"))
    private Partenaire partenaire;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_comptable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prime_objectif_exercice"))
    private ExerciceComptable exerciceComptable;

    /** Snapshot au moment de la demande - cf. javadoc de classe. */
    @NotNull
    @Column(name = "objectif_production", nullable = false, precision = 15, scale = 2)
    private BigDecimal objectifProduction;

    @NotNull
    @Column(name = "objectif_realise", nullable = false, precision = 15, scale = 2)
    private BigDecimal objectifRealise;

    @NotNull
    @Column(name = "taux_realisation", nullable = false, precision = 7, scale = 2)
    private BigDecimal tauxRealisation;

    @NotNull
    @Column(name = "montant", nullable = false)
    private Long montant;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutPrimeObjectif statut = StatutPrimeObjectif.EN_ATTENTE;

    @CreationTimestamp
    @Column(name = "date_demande", nullable = false, updatable = false)
    private Instant dateDemande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_par_id", foreignKey = @ForeignKey(name = "fk_prime_objectif_demande_par"))
    private User demandePar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par_id", foreignKey = @ForeignKey(name = "fk_prime_objectif_valide_par"))
    private User validePar;

    @Column(name = "date_validation")
    private Instant dateValidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paye_par_id", foreignKey = @ForeignKey(name = "fk_prime_objectif_paye_par"))
    private User payePar;

    @Column(name = "date_paiement")
    private Instant datePaiement;

    @Size(max = 500)
    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    /** Non-null uniquement sur la transition vers PAYEE - confirme si le SMS est bien parti, meme
     * convention que CommissionPaymentResponse.notificationEnvoyee. */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;
}
