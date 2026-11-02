package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Zone de livraison paremetrable (§8 du contrat vignette moto : "permettre d'ajouter d'autres zones
 * plus tard" - donc pas un enum Java ferme, une ligne = une zone, ajouter une zone = un insert).
 * Le client choisit directement la zone (pas de resolution automatique depuis un texte libre de
 * ville) - DemandeVignetteMoto.villeLivraison reste un champ libre pour l'adresse postale, distinct.
 */
@Entity
@Table(
        name = "t_assur_zen_frais_livraison_zone",
        uniqueConstraints = @UniqueConstraint(name = "uk_frais_livraison_zone_code", columnNames = "code")
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraisLivraisonZone extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @NotNull
    @Column(name = "montant", nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @NotNull
    @Builder.Default
    @Column(name = "actif", nullable = false)
    private Boolean actif = true;
}
