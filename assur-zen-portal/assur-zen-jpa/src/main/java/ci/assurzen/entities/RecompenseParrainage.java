package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Recompense concrete attribuee (ou en cours d'attribution) a un beneficiaire (parrain ou filleul)
 * suite a un parrainage devenu eligible. typeRecompense/montant/pourcentage sont recopies depuis la
 * RegleRecompenseParrainage source au moment de la creation (photo figee, insensible a une modification
 * ulterieure de la regle).
 */
@Entity
@Table(
        name = "t_assur_zen_recompense_parrainage",
        indexes = {
                @Index(name = "idx_recompense_parrainage_parrainage", columnList = "parrainage_id"),
                @Index(name = "idx_recompense_parrainage_beneficiaire", columnList = "beneficiaire_id"),
                @Index(name = "idx_recompense_parrainage_statut", columnList = "statut")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecompenseParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parrainage_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recompense_parrainage_parrainage"))
    private Parrainage parrainage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regle_id", foreignKey = @ForeignKey(name = "fk_recompense_parrainage_regle"))
    private RegleRecompenseParrainage regle;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiaire_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recompense_parrainage_beneficiaire"))
    private Insured beneficiaire;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_recompense", nullable = false, length = 20)
    private TypeRecompenseParrainage typeRecompense;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 15)
    private StatutRecompenseParrainage statut = StatutRecompenseParrainage.EN_ATTENTE;

    @Column(name = "montant")
    private Long montant;

    @NotNull
    @Size(max = 10)
    @Builder.Default
    @Column(name = "devise", nullable = false, length = 10)
    private String devise = "XOF";

    @Column(name = "pourcentage")
    private Double pourcentage;

    @Column(name = "points")
    private Integer points;

    @Size(max = 300)
    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "date_eligibilite")
    private Instant dateEligibilite;

    @Column(name = "date_approbation")
    private Instant dateApprobation;

    @Column(name = "date_attribution")
    private Instant dateAttribution;

    @Column(name = "date_annulation")
    private Instant dateAnnulation;

    @Column(name = "date_expiration")
    private Instant dateExpiration;

    @Size(max = 300)
    @Column(name = "motif_annulation", length = 300)
    private String motifAnnulation;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
