package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_compte_fidelite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_compte_fidelite_insured", columnNames = "insured_id")
        },
        indexes = {
                @Index(name = "idx_compte_fidelite_palier", columnList = "palier_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompteFidelite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_compte_fidelite_insured"))
    private Insured insured;

    @NotNull
    @Builder.Default
    @Column(name = "solde_points", nullable = false)
    private Long soldePoints = 0L;

    @NotNull
    @Builder.Default
    @Column(name = "points_cumules_total", nullable = false)
    private Long pointsCumulesTotal = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "palier_id", foreignKey = @ForeignKey(name = "fk_compte_fidelite_palier"))
    private PalierFidelite palier;

    @NotNull
    @Column(name = "date_adhesion", nullable = false)
    private LocalDate dateAdhesion;

    /** Score pondere calcule par ScoreFideliteService a partir des RegleFidelite actives, distinct de soldePoints/pointsCumulesTotal (le solde de points echangeables). */
    @Column(name = "score_fidelite")
    private Long scoreFidelite;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut_vip", nullable = false, length = 20)
    private StatutVip statutVip = StatutVip.NON_VIP;

    @Column(name = "date_entree_vip")
    private Instant dateEntreeVip;

    @Column(name = "date_sortie_vip")
    private Instant dateSortieVip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestionnaire_dedie_id", foreignKey = @ForeignKey(name = "fk_compte_fidelite_gestionnaire"))
    private User gestionnaireDedie;

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
