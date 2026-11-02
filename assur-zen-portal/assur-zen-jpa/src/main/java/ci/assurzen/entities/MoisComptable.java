package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Decoupage mensuel d'un ExerciceComptable (contrat "Integration des mois comptables", 2026-08-28).
 * Genere automatiquement a la creation de l'exercice (voir ExerciceComptableService/MoisComptableService),
 * un mois par tranche calendaire couverte par [dateDebut, dateFin] de l'exercice - le premier/dernier mois
 * peut donc etre partiel si l'exercice ne demarre/finit pas sur une frontiere de mois calendaire.
 * "numero" est l'ordre sequentiel dans l'exercice (1..N), pas necessairement le numero du mois calendaire,
 * pour rester correct meme sur un exercice non calque sur l'annee civile.
 */
@Entity
@Table(
        name = "t_assur_zen_mois_comptable",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mois_comptable_exercice_numero", columnNames = {"exercice_comptable_id", "numero"})
        },
        indexes = {
                @Index(name = "idx_mois_comptable_exercice", columnList = "exercice_comptable_id"),
                @Index(name = "idx_mois_comptable_date_fin", columnList = "date_fin")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoisComptable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_comptable_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mois_comptable_exercice"))
    private ExerciceComptable exerciceComptable;

    @NotNull
    @Column(nullable = false)
    private Integer numero;

    @NotBlank
    @Size(max = 30)
    @Column(name = "libelle", nullable = false, length = 30)
    private String libelle;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /**
     * Purement structurel/reporting pour ce lot (decision produit du 2026-08-28) : ne bloque PAS la
     * journalisation d'ecritures (seul ExerciceComptable.cloture le fait, cf. LedgerService). Cloture
     * automatiquement par ExerciceComptableScheduler des que dateFin est depassee, et en cascade quand
     * l'exercice parent est cloture (auto ou manuel).
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean cloture = false;

    @CreationTimestamp
    @Column(updatable = false)
    @JsonIgnore
    private Instant creation;

    @UpdateTimestamp
    @JsonIgnore
    private Instant lastUpdate;
}
