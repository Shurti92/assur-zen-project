package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_sinistre_suivis",
        indexes = {
                @Index(name = "idx_sinistre_suivi_sinistre", columnList = "sinistre_id"),
                @Index(name = "idx_sinistre_suivi_statut", columnList = "statut"),
                @Index(name = "idx_sinistre_suivi_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinistreSuivi implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinistre_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_suivi_sinistre"))
    private Sinistre sinistre;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 40)
    private SinistreStatus statut;

    @Lob
    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_suivi_auteur"))
    private User auteur;

    @Column(name = "notification_envoyee", nullable = false)
    private boolean notificationEnvoyee;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
