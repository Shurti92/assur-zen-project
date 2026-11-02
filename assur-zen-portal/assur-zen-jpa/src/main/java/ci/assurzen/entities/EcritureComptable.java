package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * En-tete d'une ecriture comptable (une operation metier equilibree : somme des debits = somme des credits).
 * Le detail des mouvements est porte par les {@link LigneEcriture} associees.
 */
@Entity
@Table(
        name = "t_assur_zen_ecriture_comptable",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ecriture_comptable_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_ecriture_contract", columnList = "contract_id"),
                @Index(name = "idx_ecriture_type_operation", columnList = "type_operation"),
                @Index(name = "idx_ecriture_date", columnList = "date_ecriture"),
                @Index(name = "idx_ecriture_exercice", columnList = "exercice_comptable_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcritureComptable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;

    @NotBlank
    @Size(max = 255)
    @Column(name = "libelle", nullable = false, length = 255)
    private String libelle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation", nullable = false, length = 20)
    private TypeOperationLedger typeOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", foreignKey = @ForeignKey(name = "fk_ecriture_contract"))
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_comptable_id", foreignKey = @ForeignKey(name = "fk_ecriture_exercice_comptable"))
    private ExerciceComptable exerciceComptable;

    /**
     * Renseigne uniquement sur l'ecriture de contrepassation elle-meme : pointe vers l'ecriture d'origine
     * qu'elle annule. Nul pour une ecriture normale.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecriture_origine_id", foreignKey = @ForeignKey(name = "fk_ecriture_origine"))
    private EcritureComptable ecritureOrigine;

    /**
     * Vrai si cette ecriture a deja ete contrepassee (empeche une double contre-passation).
     */
    @Builder.Default
    @Column(name = "contrepassee", nullable = false)
    private boolean contrepassee = false;

    @Builder.Default
    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LigneEcriture> lignes = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creation;

    public void addLigne(LigneEcriture ligne) {
        ligne.setEcriture(this);
        lignes.add(ligne);
    }
}
