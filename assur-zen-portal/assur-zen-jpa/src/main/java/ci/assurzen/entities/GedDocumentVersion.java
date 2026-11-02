package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Une version immuable du contenu d'un GedDocument. Jamais modifiee/ecrasee apres creation (meme
 * choix que FleetMovement : historique append-only, n'etend pas AuditableEntity). storedPath n'est
 * jamais expose au frontend (colonne interne au storage local uniquement).
 */
@Entity
@Table(
        name = "t_assur_zen_ged_document_versions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ged_document_version_number", columnNames = {"document_id", "version_number"})
        },
        indexes = {
                @Index(name = "idx_ged_document_version_document", columnList = "document_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GedDocumentVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ged_document_version_document"))
    private GedDocument document;

    @NotNull
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Size(max = 100)
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Size(max = 20)
    @Column(name = "extension", length = 20)
    private String extension;

    @NotNull
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @NotBlank
    @Size(max = 64)
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "storage_provider", nullable = false, length = 20)
    private GedStorageProvider storageProvider = GedStorageProvider.LOCAL;

    @NotBlank
    @Size(max = 500)
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
