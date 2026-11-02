package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Une echeance (ligne d'un echeancier de paiement fractionne) d'un contrat. N'existe que pour les
 * contrats en modePaiement = FRACTIONNE.
 */
@Entity
@Table(
        name = "t_assur_zen_echeance",
        indexes = {
                @Index(name = "idx_echeance_contract", columnList = "contract_id"),
                @Index(name = "idx_echeance_date", columnList = "date_echeance"),
                @Index(name = "idx_echeance_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Echeance implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_echeance_contract"))
    private Contract contract;

    @NotNull
    @Column(name = "numero_echeance", nullable = false)
    private Integer numeroEcheance;

    @NotNull
    @Column(name = "montant", nullable = false)
    private Long montant;

    @NotNull
    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private StatutEcheance statut = StatutEcheance.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_id", foreignKey = @ForeignKey(name = "fk_echeance_paiement"))
    private Paiement paiement;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creation;
}
