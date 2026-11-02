package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Encaissement d'une prime sur un contrat. Module minimal (le projet n'en avait aucun jusqu'ici,
 * seulement un indicateur de sante factice) : juste ce qu'il faut pour tracer un paiement recu et
 * declencher les ecritures comptables correspondantes.
 */
@Entity
@Table(
        name = "t_assur_zen_paiement",
        indexes = {
                @Index(name = "idx_paiement_contract", columnList = "contract_id"),
                @Index(name = "idx_paiement_date", columnList = "date_paiement")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paiement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_paiement_contract"))
    private Contract contract;

    @NotNull
    @Column(name = "montant", nullable = false)
    private Long montant;

    @NotNull
    @Column(name = "date_paiement", nullable = false)
    private LocalDate datePaiement;

    @Size(max = 100)
    @Column(name = "moyen_paiement", length = 100)
    private String moyenPaiement;

    @Size(max = 255)
    @Column(name = "reference", length = 255)
    private String reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_paiement_recorded_by"))
    private User recordedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creation;
}
