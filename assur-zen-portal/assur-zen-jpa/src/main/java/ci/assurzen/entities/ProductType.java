package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Type de produit (AUTO, MOTO, VIE...) auquel chaque Produit appartient. N'etend pas
 * AuditableEntity (partagee par Segments/Templates/SatisfactionImport) car ce module ne suit pas
 * qui a cree/modifie chaque type : uniquement createdAt/updatedAt/deleted/version en local.
 */
@Entity
@Table(
        name = "t_assur_zen_product_type",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_type_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_product_type_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_product_type_nom", columnNames = "nom")
        },
        indexes = {
                @Index(name = "idx_product_type_actif", columnList = "actif"),
                @Index(name = "idx_product_type_ordre", columnList = "ordre")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Builder.Default
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    private String uuid = UUID.randomUUID().toString();

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nom", nullable = false, unique = true, length = 150)
    private String nom;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 50)
    @Column(name = "icone", length = 50)
    private String icone;

    @Size(max = 20)
    @Column(name = "couleur", length = 20)
    private String couleur;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Type seede par defaut (ex. AUTRES) : non supprimable, meme sans produit rattache. */
    @Builder.Default
    @Column(name = "systeme", nullable = false)
    private boolean systeme = false;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
