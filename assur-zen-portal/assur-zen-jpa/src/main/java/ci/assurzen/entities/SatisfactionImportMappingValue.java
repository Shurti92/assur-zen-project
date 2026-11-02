package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;

/**
 * Table de correspondance texte-brut -> valeur cible pour un mapping donne (ex: pour un mapping
 * cible=SCORE sur la colonne "8. Recommanderiez-vous...", "Oui, certainement" -> "9"). Necessaire
 * car les formulaires Google Forms reels n'exportent quasiment jamais une note 0-10 brute — plutot
 * des reponses textuelles/etoiles a choix multiple (constate sur un vrai export le 2026-08-06).
 */
@Entity
@Table(
        name = "t_assur_zen_satisfaction_import_mapping_value",
        indexes = {
                @Index(name = "idx_import_mapping_value_mapping", columnList = "mapping_id")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionImportMappingValue extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id", nullable = false, foreignKey = @ForeignKey(name = "fk_import_mapping_value_mapping"))
    private SatisfactionImportMapping mapping;

    @NotBlank
    @Size(max = 300)
    @Column(name = "valeur_source", nullable = false, length = 300)
    private String valeurSource;

    @NotBlank
    @Size(max = 50)
    @Column(name = "valeur_cible", nullable = false, length = 50)
    private String valeurCible;
}
