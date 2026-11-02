package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Trace chaque changement de statut d'une DemandeVignetteMoto (contrat "Suivi d'evolution des
 * demandes de vignettes", 2026-09-17) - meme patron que HistoriqueParrainage (ancien/nouveau statut
 * typés, auteur, motif), jamais purge/modifie apres coup (append-only).
 */
@Entity
@Table(
        name = "t_assur_zen_historique_demande_vignette_moto",
        indexes = {
                @Index(name = "idx_historique_demande_vignette_moto_demande", columnList = "demande_vignette_moto_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueDemandeVignetteMoto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_vignette_moto_id", nullable = false, foreignKey = @ForeignKey(name = "fk_historique_demande_vignette_moto_demande"))
    private DemandeVignetteMoto demandeVignetteMoto;

    /** Null uniquement pour le tout premier evenement (creation, pas de statut anterieur). */
    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_statut", length = 25)
    private StatutDemandeVignetteMoto ancienStatut;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_statut", nullable = false, length = 25)
    private StatutDemandeVignetteMoto nouveauStatut;

    @Size(max = 500)
    @Column(name = "motif", length = 500)
    private String motif;

    @Size(max = 50)
    @Column(name = "reference_operation", length = 50)
    private String referenceOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", foreignKey = @ForeignKey(name = "fk_historique_demande_vignette_moto_auteur"))
    private User auteur;

    /** Resultat de l'envoi du SMS de notification declenche par ce changement (null si aucun SMS
     * n'a ete tente, ex. telephoneAssure absent). */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
