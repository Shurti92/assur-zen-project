package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Commission par contrat versee au COMMERCIAL INTERNE (Contract.salesRep) qui a vendu le contrat, en
 * plus de son salaire+bonus mensuel (contrat "Commission commerciale interne", 2026-09-08 - revient sur
 * la decision initiale du module Commercial qui excluait toute commission par contrat). Mirroir de
 * ProduitCommissionTaux (meme forme montant/taux via libTauxCommission), mais sans dimension
 * TypePartenaire : un seul taux par produit, applicable uniquement quand le contrat n'a PAS de
 * partenaire (Contract.partenaire == null) - meme logique d'exclusion mutuelle que Contract.partnerShare
 * (vente via partenaire : la commission revient au partenaire, pas au commercial qui l'a simplement
 * traite techniquement, cf. ContractsService).
 */
@Entity
@Table(
        name = "t_assur_zen_produit_commission_commercial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_produit_commission_commercial_produit", columnNames = "produit_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProduitCommissionCommercial implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_produit_commission_commercial_produit"))
    private Produit produit;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Digits(integer = 10, fraction = 4)
    @Column(name = "taux_commission", nullable = false, precision = 14, scale = 4)
    private BigDecimal tauxCommission;

    /** "%" (pourcentage applique a la prime) ou un libelle de devise/montant fixe (ex "FCFA") - meme
     * convention que ProduitCommissionTaux.libTauxCommission. */
    @NotBlank
    @Size(max = 10)
    @Column(name = "lib_taux_commission", nullable = false, length = 10)
    private String libTauxCommission;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonIgnore
    private Instant creation;

    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;
}
