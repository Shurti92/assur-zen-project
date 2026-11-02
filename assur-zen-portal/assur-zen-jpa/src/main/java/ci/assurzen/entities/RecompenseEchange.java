package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_recompense_echange",
        indexes = {
                @Index(name = "idx_echange_compte", columnList = "compte_fidelite_id"),
                @Index(name = "idx_echange_recompense", columnList = "recompense_id"),
                @Index(name = "idx_echange_statut", columnList = "statut")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecompenseEchange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compte_fidelite_id", nullable = false, foreignKey = @ForeignKey(name = "fk_echange_compte"))
    private CompteFidelite compteFidelite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recompense_id", nullable = false, foreignKey = @ForeignKey(name = "fk_echange_recompense"))
    private Recompense recompense;

    @NotNull
    @Column(name = "points_utilises", nullable = false)
    private Long pointsUtilises;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private EchangeStatut statut;

    @NotNull
    @Column(name = "date_echange", nullable = false)
    private Instant dateEchange;

    @Column(name = "date_traitement")
    private Instant dateTraitement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traite_par", foreignKey = @ForeignKey(name = "fk_echange_traite_par"))
    private User traitePar;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
