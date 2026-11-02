package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
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
import java.util.HashSet;
import java.util.Set;

/**
 * Programme de parrainage : definit la fenetre de validite, les quotas et les produits eligibles.
 * Les regles de recompense associees (RegleRecompenseParrainage) sont configurables independamment,
 * pas codees en dur ici - meme separation que CrossSellRule/CrossSellRuleCondition.
 */
@Entity
@Table(
        name = "t_assur_zen_programme_parrainage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_programme_parrainage_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_programme_parrainage_statut", columnList = "statut")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammeParrainage implements Serializable {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 20)
    private StatutProgrammeParrainage statut = StatutProgrammeParrainage.BROUILLON;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    /** Nombre maximum de parrainages qu'un meme parrain peut faire aboutir dans ce programme ; null = illimite. */
    @Column(name = "max_parrainages_par_client")
    private Integer maxParrainagesParClient;

    /** Nombre maximum de recompenses qu'un meme parrain peut recevoir dans ce programme ; null = illimite. */
    @Column(name = "max_recompenses_par_client")
    private Integer maxRecompensesParClient;

    /** Delai minimum (jours) entre deux parrainages crees par le meme parrain ; null/0 = pas de delai de carence. */
    @Column(name = "delai_carence_jours")
    private Integer delaiCarenceJours;

    /** Duree de validite (jours) d'un parrainage avant expiration automatique ; null = pas d'expiration. */
    @Column(name = "duree_validite_jours")
    private Integer dureeValiditeJours;

    /** Prime annuelle minimale du contrat du filleul pour que le parrainage soit eligible ; null = pas de seuil. */
    @Column(name = "prime_minimum")
    private Long primeMinimum;

    /** Produits eligibles ; vide = tous les produits sont eligibles. */
    @ElementCollection
    @CollectionTable(
            name = "t_assur_zen_programme_parrainage_produit",
            joinColumns = @JoinColumn(name = "programme_id", foreignKey = @ForeignKey(name = "fk_programme_parrainage_produit_programme"))
    )
    @Column(name = "produit_id")
    @Builder.Default
    private Set<Long> produitsEligibles = new HashSet<>();

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
