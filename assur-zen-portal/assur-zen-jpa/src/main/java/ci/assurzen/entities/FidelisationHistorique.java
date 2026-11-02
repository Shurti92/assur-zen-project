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
        name = "t_assur_zen_fidelisation_historique",
        indexes = {
                @Index(name = "idx_historique_compte", columnList = "compte_fidelite_id"),
                @Index(name = "idx_historique_type", columnList = "type_mouvement"),
                @Index(name = "idx_historique_contrat", columnList = "contrat_id"),
                @Index(name = "idx_historique_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FidelisationHistorique implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compte_fidelite_id", nullable = false, foreignKey = @ForeignKey(name = "fk_historique_compte"))
    private CompteFidelite compteFidelite;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_mouvement", nullable = false, length = 20)
    private MouvementFideliteType typeMouvement;

    @NotNull
    @Column(name = "points", nullable = false)
    private Long points;

    @NotNull
    @Column(name = "solde_apres", nullable = false)
    private Long soldeApres;

    @Size(max = 300)
    @Column(name = "motif", length = 300)
    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_id", foreignKey = @ForeignKey(name = "fk_historique_contrat"))
    private Contract contrat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", foreignKey = @ForeignKey(name = "fk_historique_auteur"))
    private User auteur;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
