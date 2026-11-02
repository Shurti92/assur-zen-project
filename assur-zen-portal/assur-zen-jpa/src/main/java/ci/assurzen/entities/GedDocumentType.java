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
 * Type de document GED (CONTRAT, ATTESTATION, PIECE_IDENTITE...). Meme patron que ProductType :
 * n'etend pas AuditableEntity car ce module ne trace pas qui a cree/modifie chaque type de
 * reference, uniquement createdAt/updatedAt/deleted/version en local.
 */
@Entity
@Table(
        name = "t_assur_zen_ged_document_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ged_document_type_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_ged_document_type_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_ged_document_type_actif", columnList = "actif")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GedDocumentType implements Serializable {

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
    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Type seede par defaut (AUTRE) : non supprimable, sert de repli quand aucun type n'est fourni. */
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
