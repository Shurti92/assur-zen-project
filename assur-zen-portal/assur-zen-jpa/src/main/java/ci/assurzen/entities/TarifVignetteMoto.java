package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Grille tarifaire paremetrable des vignettes moto (montant vignette + frais d'operation), variable
 * selon l'age de la moto et sa cylindree. Une demande fige ces montants a la creation
 * (DemandeVignetteMoto.montantVignette/fraisOperation) - modifier une ligne ici n'affecte jamais une
 * demande deja creee, cf. DemandeVignetteMotoService.creer().
 */
@Entity
@Table(
        name = "t_assur_zen_tarif_vignette_moto",
        indexes = {
                @Index(name = "idx_tarif_vignette_moto_categories", columnList = "categorie_age, categorie_cylindree"),
                @Index(name = "idx_tarif_vignette_moto_actif", columnList = "actif")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarifVignetteMoto extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_age", nullable = false, length = 20)
    private CategorieAgeMoto categorieAge;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_cylindree", nullable = false, length = 20)
    private CategorieCylindree categorieCylindree;

    @NotNull
    @Column(name = "montant_vignette", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantVignette;

    @NotNull
    @Column(name = "frais_operation", nullable = false, precision = 12, scale = 2)
    private BigDecimal fraisOperation;

    @NotNull
    @Column(name = "date_debut_validite", nullable = false)
    private LocalDate dateDebutValidite;

    /** Null = validite ouverte (pas de date de fin connue). */
    @Column(name = "date_fin_validite")
    private LocalDate dateFinValidite;

    @NotNull
    @Builder.Default
    @Column(name = "actif", nullable = false)
    private Boolean actif = true;
}
