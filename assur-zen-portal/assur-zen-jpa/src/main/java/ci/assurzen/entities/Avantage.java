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
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catalogue des avantages VIP (gestionnaire dedie, traitement prioritaire...), distincts des
 * Recompense (echangeables contre des points) : un avantage est soit automatiquement attribue en
 * atteignant un palier (palier renseigne), soit attribuable manuellement (palier null).
 */
@Entity
@Table(
        name = "t_assur_zen_avantage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_avantage_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_avantage_palier", columnList = "palier_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Avantage implements Serializable {

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
    @Column(name = "type", nullable = false, length = 30)
    private AvantageType type;

    /** Si renseigne : avantage automatiquement attribue/revoque par ScoreFideliteService a l'entree/sortie de ce palier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "palier_id", foreignKey = @ForeignKey(name = "fk_avantage_palier"))
    private PalierFidelite palier;

    @Column(name = "valeur", precision = 12, scale = 2)
    private BigDecimal valeur;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
