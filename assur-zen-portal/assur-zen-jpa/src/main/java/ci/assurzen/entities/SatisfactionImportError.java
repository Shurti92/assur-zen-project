package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;

/** ligneNumero = 0 pour une erreur au niveau du fichier entier (encodage/separateur/colonne manquante), sinon numero de ligne CSV (1-based, hors en-tete). */
@Entity
@Table(
        name = "t_assur_zen_satisfaction_import_error",
        indexes = {
                @Index(name = "idx_import_error_import", columnList = "import_id"),
                @Index(name = "idx_import_error_type", columnList = "type_erreur")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionImportError extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false, foreignKey = @ForeignKey(name = "fk_import_error_import"))
    private SatisfactionImport satisfactionImport;

    @NotNull
    @Column(name = "ligne_numero", nullable = false)
    private Integer ligneNumero;

    @Size(max = 100)
    @Column(name = "colonne_en_erreur", length = 100)
    private String colonneEnErreur;

    @Size(max = 500)
    @Column(name = "valeur_brute", length = 500)
    private String valeurBrute;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_erreur", nullable = false, length = 40)
    private ImportErrorType typeErreur;

    @Size(max = 1000)
    @Column(name = "message", length = 1000)
    private String message;
}
