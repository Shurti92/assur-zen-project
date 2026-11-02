package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_palier_fidelite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_palier_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_palier_seuil", columnList = "seuil_points")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PalierFidelite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @NotNull
    @Column(name = "seuil_points", nullable = false)
    private Long seuilPoints;

    @Lob
    @Column(name = "avantages", columnDefinition = "TEXT")
    private String avantages;

    @Size(max = 20)
    @Column(name = "couleur", length = 20)
    private String couleur;

    @Size(max = 50)
    @Column(name = "icone", length = 50)
    private String icone;

    @Builder.Default
    @Column(name = "est_vip", nullable = false)
    private boolean estVip = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
