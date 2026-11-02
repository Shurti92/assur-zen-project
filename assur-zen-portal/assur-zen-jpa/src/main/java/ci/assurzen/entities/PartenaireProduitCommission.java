package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Commission configuree en MONTANT FCFA par produit et par beneficiaire (decision utilisateur du
 * 2026-09-25) :
 * - PARTENAIRE : pour un partenaire precis (partenaire renseigne) ou, a defaut, pour tous les
 *   partenaires d'un type (typePartenaire renseigne, partenaire null) ;
 * - COMMERCIAL : pour un commercial interne precis (commercial = User salesRep).
 * Utilisee par ContractsService quand la commission n'est pas saisie sur le contrat. Distincte de
 * t_assur_zen_produit_commission_taux (commission AssurZen) et de
 * t_assur_zen_produit_commission_commercial (commission commerciale par defaut du produit).
 * dateDebut/dateFin facultatives (null = sans borne).
 */
@Entity
@Table(
        name = "t_assur_zen_partenaire_produit_commission",
        indexes = @Index(name = "idx_partenaire_produit_commission_produit", columnList = "produit_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartenaireProduitCommission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_partenaire_produit_commission_produit"))
    private Produit produit;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiaire", nullable = false, length = 20)
    private BeneficiaireCommission beneficiaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", foreignKey = @ForeignKey(name = "fk_partenaire_produit_commission_partenaire"))
    private Partenaire partenaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_partenaire", length = 20)
    private PartenaireType typePartenaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", foreignKey = @ForeignKey(name = "fk_partenaire_produit_commission_commercial"))
    private User commercial;

    @NotNull
    @Min(0)
    @Column(name = "montant", nullable = false)
    private Long montant;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonIgnore
    private Instant creation;

    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;

    /** Active et valable a la date donnee (bornes incluses, null = sans borne). */
    public boolean estApplicableLe(LocalDate date) {
        return active
                && (dateDebut == null || !date.isBefore(dateDebut))
                && (dateFin == null || !date.isAfter(dateFin));
    }
}
