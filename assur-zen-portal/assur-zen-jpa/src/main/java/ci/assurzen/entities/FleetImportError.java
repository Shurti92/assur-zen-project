package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(
        name = "t_assur_zen_fleet_import_errors",
        indexes = {
                @Index(name = "idx_fleet_import_error_import", columnList = "fleet_import_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetImportError implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_import_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fleet_import_error_import"))
    private FleetImport fleetImport;

    @NotNull
    @Column(name = "ligne_numero", nullable = false)
    private Integer ligneNumero;

    @Size(max = 100)
    @Column(name = "colonne_en_erreur", length = 100)
    private String colonneEnErreur;

    @Size(max = 500)
    @Column(name = "valeur_brute", length = 500)
    private String valeurBrute;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "message", nullable = false, length = 1000)
    private String message;
}
