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

@Entity
@Table(
        name = "t_assur_zen_sinistre_photos",
        indexes = {
                @Index(name = "idx_sinistre_photo_sinistre", columnList = "sinistre_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinistrePhoto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** PHOTO ou DOCUMENT pour les pieces envoyees depuis le mobile (2026-11-02) ; null = photo du back-office. */
    @Column(name = "kind", length = 20)
    private String kind;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinistre_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_photo_sinistre"))
    private Sinistre sinistre;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Size(max = 500)
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Size(max = 100)
    @Column(name = "content_type", length = 100)
    private String contentType;

    @NotNull
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", foreignKey = @ForeignKey(name = "fk_sinistre_photo_uploaded_by"))
    private User uploadedBy;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
