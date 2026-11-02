package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "t_assur_zen_communication_template",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_template_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_template_uuid", columnNames = "uuid")
        },
        indexes = {
                @Index(name = "idx_template_canal", columnList = "canal"),
                @Index(name = "idx_template_statut", columnList = "statut"),
                @Index(name = "idx_template_categorie", columnList = "categorie_id"),
                @Index(name = "idx_template_langue", columnList = "langue_id")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunicationTemplate extends AuditableEntity implements Serializable {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalEnvoi canal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false, foreignKey = @ForeignKey(name = "fk_template_categorie"))
    private TemplateCategory categorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "langue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_template_langue"))
    private Langue langue;

    @Size(max = 200)
    @Column(name = "objet", length = 200)
    private String objet;

    @NotBlank
    @Lob
    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private TemplateStatut statut = TemplateStatut.BROUILLON;

    @Column(name = "max_caracteres")
    private Integer maxCaracteres;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_assur_zen_communication_template_variable_usage",
            joinColumns = @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_template_usage_template")),
            inverseJoinColumns = @JoinColumn(name = "variable_id", foreignKey = @ForeignKey(name = "fk_template_usage_variable")),
            uniqueConstraints = @UniqueConstraint(name = "uk_template_variable_usage", columnNames = {"template_id", "variable_id"})
    )
    private Set<TemplateVariable> variables = new HashSet<>();
}
