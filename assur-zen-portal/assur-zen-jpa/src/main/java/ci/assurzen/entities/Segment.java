package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(
        name = "t_assur_zen_communication_segment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_segment_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_segment_uuid", columnNames = "uuid")
        },
        indexes = {
                @Index(name = "idx_segment_type", columnList = "type"),
                @Index(name = "idx_segment_active", columnList = "active")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Segment extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * String plutot que java.util.UUID : Hibernate lie java.util.UUID en BINARY(16) par defaut
     * (UUIDJdbcType), incompatible avec une colonne char(36) — constate en test E2E
     * (MysqlDataTruncation sur l'insert).
     */
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
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SegmentType type;

    @Builder.Default
    @Column(name = "priorite", nullable = false)
    private Integer priorite = 0;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    @Column(name = "couleur", length = 7)
    private String couleur;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
