package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;

/** Une ligne = "cette colonne CSV alimente ce champ cible" pour un profil de mapping donne. */
@Entity
@Table(
        name = "t_assur_zen_satisfaction_import_mapping",
        indexes = {
                @Index(name = "idx_import_mapping_profile", columnList = "profile_id")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionImportMapping extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_import_mapping_profile"))
    private SatisfactionImportMappingProfile profile;

    @NotBlank
    @Size(max = 150)
    @Column(name = "csv_column_name", nullable = false, length = 150)
    private String csvColumnName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", nullable = false, length = 30)
    private SatisfactionImportField targetField;

    @Builder.Default
    @Column(name = "obligatoire", nullable = false)
    private boolean obligatoire = false;

    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;
}
