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

/** Type de sortie de fonds (LOAN, ADVANCE, EXPENSE...). Meme patron que GedDocumentType/ProductType :
 * entite de reference simple, pas d'AuditableEntity. */
@Entity
@Table(
        name = "t_assur_zen_fund_outflow_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fund_outflow_type_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_fund_outflow_type_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_fund_outflow_type_actif", columnList = "actif")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundOutflowType implements Serializable {

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
