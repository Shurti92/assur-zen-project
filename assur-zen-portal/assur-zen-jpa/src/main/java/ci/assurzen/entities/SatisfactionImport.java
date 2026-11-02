package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "t_assur_zen_satisfaction_import",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_satisfaction_import_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_satisfaction_import_uuid", columnNames = "uuid")
        },
        indexes = {
                @Index(name = "idx_satisfaction_import_statut", columnList = "statut"),
                @Index(name = "idx_satisfaction_import_source", columnList = "source"),
                @Index(name = "idx_satisfaction_import_survey", columnList = "survey_id"),
                @Index(name = "idx_satisfaction_import_execute_par", columnList = "execute_par")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionImport extends AuditableEntity implements Serializable {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_satisfaction_import_profile"))
    private SatisfactionImportMappingProfile mappingProfile;

    @Size(max = 50)
    @Column(name = "source", length = 50)
    private String source;

    @Size(max = 100)
    @Column(name = "survey_id", length = 100)
    private String surveyId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Size(max = 500)
    @Column(name = "stored_file_path", nullable = false, length = 500)
    private String storedFilePath;

    @NotBlank
    @Size(max = 1)
    @Column(name = "delimiter", nullable = false, length = 1)
    private String delimiter;

    @NotBlank
    @Size(max = 30)
    @Column(name = "encoding", nullable = false, length = 30)
    private String encoding;

    @Builder.Default
    @Column(name = "has_header", nullable = false)
    private boolean hasHeader = true;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_strategy", nullable = false, length = 20)
    private DuplicateStrategy duplicateStrategy;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private SatisfactionImportStatut statut = SatisfactionImportStatut.PENDING;

    @Column(name = "total_lignes")
    private Integer totalLignes;

    @Column(name = "lignes_ignorees")
    private Integer lignesIgnorees;

    @Column(name = "lignes_reussies")
    private Integer lignesReussies;

    @Column(name = "lignes_en_erreur")
    private Integer lignesEnErreur;

    /** Recalcule a chaque execution/retry, sur les lignes traitees avec succes de CE run (pas de lien direct stocke vers EnqueteSatisfaction). */
    @Column(name = "promoteurs")
    private Integer promoteurs;

    @Column(name = "passifs")
    private Integer passifs;

    @Column(name = "detracteurs")
    private Integer detracteurs;

    @Column(name = "score_nps_moyen")
    private Double scoreNpsMoyen;

    @Column(name = "date_debut_traitement")
    private Instant dateDebutTraitement;

    @Column(name = "date_fin_traitement")
    private Instant dateFinTraitement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execute_par", foreignKey = @ForeignKey(name = "fk_satisfaction_import_execute_par"))
    private User executePar;
}
