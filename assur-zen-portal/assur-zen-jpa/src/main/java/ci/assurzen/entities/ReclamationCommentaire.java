package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_reclamation_commentaires",
        indexes = {
                @Index(name = "idx_reclamation_commentaire_reclamation", columnList = "reclamation_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReclamationCommentaire implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reclamation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reclamation_commentaire_reclamation"))
    private Reclamation reclamation;

    @NotBlank
    @Lob
    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reclamation_commentaire_auteur"))
    private User auteur;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
