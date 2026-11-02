package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Avenant a un contrat deja signe : trace formellement, numerotee et classee (TypeAvenant), toute
 * modification touchant les champs sensibles (prime, commission, partenaire, compagnie) d'un contrat
 * en cours - contrat "Espace Partenaire", decisions produit du 2026-08-23. Une fois le contrat signe,
 * ces champs ne sont plus modifiables via PUT /contracts/{id} (cf. ContractsService.update()), seul cet
 * avenant peut les faire evoluer. Les autres types (garanties, bien assure, conducteur, zone, etc.) ne
 * correspondent a aucun champ structure du schema actuel : ils sont uniquement traces via
 * motif/description en texte libre, la modelisation fine de ces domaines etant hors perimetre ici.
 */
@Entity
@Table(
        name = "t_assur_zen_avenant",
        indexes = {
                @Index(name = "idx_avenant_contract", columnList = "contract_id"),
                @Index(name = "idx_avenant_type", columnList = "type"),
                @Index(name = "idx_avenant_categorie", columnList = "categorie")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avenant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_avenant_contract"))
    private Contract contract;

    /** Sequentiel par contrat (1, 2, 3...) - cf. AvenantService.creer(). */
    @NotNull
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TypeAvenant type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 20)
    private CategorieAvenant categorie;

    @NotNull
    @Column(name = "date_effet", nullable = false)
    private LocalDate dateEffet;

    @NotBlank
    @Size(max = 500)
    @Column(name = "motif", nullable = false, length = 500)
    private String motif;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ancien_premium")
    private Long ancienPremium;

    @Column(name = "nouveau_premium")
    private Long nouveauPremium;

    @Column(name = "ancienne_commission_rate", precision = 8, scale = 4)
    private BigDecimal ancienneCommissionRate;

    @Column(name = "nouvelle_commission_rate", precision = 8, scale = 4)
    private BigDecimal nouvelleCommissionRate;

    @Column(name = "ancien_commission_amount")
    private Long ancienCommissionAmount;

    @Column(name = "nouveau_commission_amount")
    private Long nouveauCommissionAmount;

    @Column(name = "ancien_partner_share")
    private Long ancienPartnerShare;

    @Column(name = "nouveau_partner_share")
    private Long nouveauPartnerShare;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancien_partenaire_id", foreignKey = @ForeignKey(name = "fk_avenant_ancien_partenaire"))
    private Partenaire ancienPartenaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nouveau_partenaire_id", foreignKey = @ForeignKey(name = "fk_avenant_nouveau_partenaire"))
    private Partenaire nouveauPartenaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancienne_compagnie_id", foreignKey = @ForeignKey(name = "fk_avenant_ancienne_compagnie"))
    private Compagnie ancienneCompagnie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nouvelle_compagnie_id", foreignKey = @ForeignKey(name = "fk_avenant_nouvelle_compagnie"))
    private Compagnie nouvelleCompagnie;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_avenant_created_by"))
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
