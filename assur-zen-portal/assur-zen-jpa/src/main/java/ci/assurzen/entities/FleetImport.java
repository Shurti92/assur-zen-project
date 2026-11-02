package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.time.Instant;

/**
 * Import massif de vehicules dans une flotte (CSV/XLSX). Flux synchrone en 2 temps :
 * preview() (validation pure, aucune ecriture Vehicule/FleetVehicle) puis confirm() (ecriture reelle).
 * fileChecksum (SHA-256) protege contre le double-import accidentel du meme fichier.
 */
@Entity
@Table(
        name = "t_assur_zen_fleet_imports",
        indexes = {
                @Index(name = "idx_fleet_import_fleet", columnList = "fleet_id"),
                @Index(name = "idx_fleet_import_checksum", columnList = "file_checksum"),
                @Index(name = "idx_fleet_import_status", columnList = "status")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetImport extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_import_fleet"))
    private Fleet fleet;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Size(max = 500)
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @NotBlank
    @Size(max = 64)
    @Column(name = "file_checksum", nullable = false, length = 64)
    private String fileChecksum;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private FleetImportFormat format;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private FleetImportStatus status = FleetImportStatus.UPLOADED;

    @Builder.Default
    @Column(name = "total_rows")
    private Integer totalRows = 0;

    @Builder.Default
    @Column(name = "valid_rows")
    private Integer validRows = 0;

    @Builder.Default
    @Column(name = "invalid_rows")
    private Integer invalidRows = 0;

    @Builder.Default
    @Column(name = "duplicate_rows")
    private Integer duplicateRows = 0;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "error_count")
    private Integer errorCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", foreignKey = @ForeignKey(name = "fk_fleet_import_uploaded_by"))
    private User uploadedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
