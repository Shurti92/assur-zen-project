package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Profil de mapping CSV reutilisable (ex: "Google Forms - Satisfaction Souscription"),
 * reference par id a chaque import plutot que soumis en ligne a chaque fois.
 */
@Entity
@Table(
        name = "t_assur_zen_satisfaction_import_mapping_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_import_profile_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_import_profile_uuid", columnNames = "uuid")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionImportMappingProfile extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** String plutot que java.util.UUID : voir Segment.uuid pour le detail du bug evite. */
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

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
