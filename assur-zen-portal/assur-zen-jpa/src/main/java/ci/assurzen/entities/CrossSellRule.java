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
import java.time.LocalDate;

/**
 * Regle de vente croisee configurable : "SI [conditions -> CrossSellRuleCondition] ALORS recommander
 * produitRecommande". Aucune regle n'est codee en dur ; seul le garde-fou "ne jamais recommander un
 * produit deja detenu" est un comportement fixe du moteur (CrossSellRuleEvaluator), pas une condition.
 */
@Entity
@Table(
        name = "t_assur_zen_cross_sell_rule",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cross_sell_rule_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_cross_sell_rule_actif", columnList = "actif"),
                @Index(name = "idx_cross_sell_rule_produit_recommande", columnList = "produit_recommande_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossSellRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Produit devant deja etre detenu (contrat signe) pour que la regle soit evaluee ; null = s'applique quel que soit le produit actuel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_declencheur_id", foreignKey = @ForeignKey(name = "fk_cross_sell_rule_produit_declencheur"))
    private Produit produitDeclencheur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_recommande_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cross_sell_rule_produit_recommande"))
    private Produit produitRecommande;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "priorite", nullable = false, length = 20)
    private ReclamationPriorite priorite = ReclamationPriorite.NORMALE;

    /** Score (0-100) attribue a toute opportunite generee par cette regle. */
    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "poids_score", nullable = false)
    private Integer poidsScore;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
