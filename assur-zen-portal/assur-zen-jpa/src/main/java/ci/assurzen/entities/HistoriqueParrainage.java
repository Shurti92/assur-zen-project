package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_historique_parrainage",
        indexes = {
                @Index(name = "idx_historique_parrainage_parrainage", columnList = "parrainage_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parrainage_id", nullable = false, foreignKey = @ForeignKey(name = "fk_historique_parrainage_parrainage"))
    private Parrainage parrainage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", nullable = false, length = 30)
    private TypeEvenementParrainage typeEvenement;

    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_statut", length = 25)
    private StatutParrainage ancienStatut;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_statut", length = 25)
    private StatutParrainage nouveauStatut;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", foreignKey = @ForeignKey(name = "fk_historique_parrainage_auteur"))
    private User auteur;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
