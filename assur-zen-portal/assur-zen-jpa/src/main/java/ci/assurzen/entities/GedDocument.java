package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

/**
 * Document logique GED (module transversal). Ne contient jamais le fichier physiquement - le
 * contenu vit dans GedDocumentVersion (une ou plusieurs versions par document, jamais ecrasees).
 * Le rattachement a un objet metier (client/contrat/vehicule/flotte/sinistre/reclamation...) passe
 * par GedDocumentLink (entity_type/entity_id generique), pas par une FK directe ici, pour pouvoir
 * accueillir de nouveaux modules sans modifier cette table.
 */
@Entity
@Table(
        name = "t_assur_zen_ged_documents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ged_document_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_ged_document_number", columnNames = "document_number")
        },
        indexes = {
                @Index(name = "idx_ged_document_type", columnList = "document_type_id"),
                @Index(name = "idx_ged_document_status", columnList = "status"),
                @Index(name = "idx_ged_document_created_at", columnList = "created_at"),
                @Index(name = "idx_ged_document_created_by", columnList = "created_by"),
                @Index(name = "idx_ged_document_checksum", columnList = "checksum_sha256")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GedDocument extends AuditableEntity implements Serializable {

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
    @Size(max = 20)
    @Column(name = "document_number", nullable = false, unique = true, length = 20)
    private String documentNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ged_document_type"))
    private GedDocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_ged_document_category"))
    private GedDocumentCategory category;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private GedDocumentStatus status = GedDocumentStatus.UPLOADED;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "confidentiality_level", nullable = false, length = 20)
    private GedConfidentialityLevel confidentialityLevel = GedConfidentialityLevel.INTERNAL;

    @Size(max = 50)
    @Column(name = "source", length = 50)
    private String source;

    @Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Size(max = 64)
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Size(max = 100)
    @Column(name = "archived_by", length = 100)
    private String archivedBy;
}
