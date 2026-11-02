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
 * Document administratif joint a la fiche d'un partenaire (contrat d'apporteur, CNI, RIB,
 * justificatif de domicile...). Meme forme que ReclamationPieceJointe/SinistrePhoto, avec en plus
 * une categorie ("type") - volontairement une String libre (pas un enum Java) : le contrat d'API
 * demande explicitement une liste extensible sans casser le front, qui affiche une valeur inconnue
 * telle quelle plutot que de planter.
 */
@Entity
@Table(
        name = "t_assur_zen_partenaire_documents",
        indexes = {
                @Index(name = "idx_partenaire_document_partenaire", columnList = "partenaire_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartenaireDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", nullable = false, foreignKey = @ForeignKey(name = "fk_partenaire_document_partenaire"))
    private Partenaire partenaire;

    @NotBlank
    @Size(max = 50)
    @Column(name = "type", nullable = false, length = 50)
    private String type;

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
    @JoinColumn(name = "uploaded_by", foreignKey = @ForeignKey(name = "fk_partenaire_document_uploaded_by"))
    private User uploadedBy;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Date de fin de validite du document (CNI, permis...) - 2026-10-03, pour signaler une piece
     * expiree. NULL = pas d'expiration connue. */
    @Column(name = "date_expiration")
    private java.time.LocalDate dateExpiration;

    /** Verification par AssurZen (2026-10-04). NULL = document anterieur, traite comme EN_ATTENTE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_validation", length = 20, columnDefinition = "varchar(20)")
    private StatutValidationDocument statutValidation;

    @Column(name = "motif_refus", length = 500)
    private String motifRefus;

    @Column(name = "valide_le")
    private Instant valideLe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par_id")
    private User validePar;
}
