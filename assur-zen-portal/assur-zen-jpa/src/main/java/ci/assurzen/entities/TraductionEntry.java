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

/**
 * Une entree du dictionnaire de traductions i18n runtime (voir docs/api-contracts/i18n.md du repo
 * frontend). cle+langue est unique : une cle porte exactement une valeur par langue. scope est un
 * regroupement fonctionnel libre (ex. "sinistres") utilise par le frontend pour le lazy-loading par
 * module, independant du referentiel Langue existant (code/libelle/actif, inchange).
 */
@Entity
@Table(
        name = "t_assur_zen_traduction_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_traduction_cle_langue", columnNames = {"cle", "langue_id"})
        },
        indexes = {
                @Index(name = "idx_traduction_langue", columnList = "langue_id"),
                @Index(name = "idx_traduction_scope", columnList = "scope")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraductionEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    @Column(name = "cle", nullable = false, length = 200)
    private String cle;

    @NotBlank
    @Size(max = 50)
    @Column(name = "scope", nullable = false, length = 50)
    private String scope;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "langue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_traduction_langue"))
    private Langue langue;

    @NotBlank
    @Lob
    @Column(name = "valeur", nullable = false, columnDefinition = "TEXT")
    private String valeur;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
