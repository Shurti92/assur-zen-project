package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_cross_sell_opportunity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cross_sell_opportunity_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_cross_sell_opportunity_insured", columnList = "insured_id"),
                @Index(name = "idx_cross_sell_opportunity_statut", columnList = "statut"),
                @Index(name = "idx_cross_sell_opportunity_commercial", columnList = "commercial_id"),
                @Index(name = "idx_cross_sell_opportunity_regle", columnList = "regle_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossSellOpportunity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_insured"))
    private Insured insured;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_id", foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_contrat"))
    private Contract contrat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_actuel_id", foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_produit_actuel"))
    private Produit produitActuel;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_recommande_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_produit_recommande"))
    private Produit produitRecommande;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "score", nullable = false)
    private Integer score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regle_id", foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_regle"))
    private CrossSellRule regle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_commercial"))
    private User commercial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campagne_id", foreignKey = @ForeignKey(name = "fk_cross_sell_opportunity_campagne"))
    private CampagneMarketing campagne;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "priorite", nullable = false, length = 20)
    private ReclamationPriorite priorite = ReclamationPriorite.NORMALE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 20)
    private CrossSellStatut statut = CrossSellStatut.NOUVELLE;

    @Lob
    @Column(name = "motif", columnDefinition = "TEXT")
    private String motif;

    @Lob
    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "date_affectation")
    private Instant dateAffectation;

    @Column(name = "date_traitement")
    private Instant dateTraitement;

    @Column(name = "date_cloture")
    private Instant dateCloture;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
