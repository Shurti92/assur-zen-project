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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_exercice_comptable",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_exercice_comptable_libelle", columnNames = "libelle")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciceComptable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "libelle", nullable = false, unique = true, length = 100)
    private String libelle;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Distinct de "active" : un exercice cloture est definitivement verrouille (plus aucune ecriture
     * comptable ne peut y etre journalisee), independamment du fait qu'il soit ou non l'exercice courant.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean cloture = false;

    /**
     * Objectif de production (primes) global fixe pour l'ensemble de l'exercice, tous partenaires et
     * contrats confondus. Distinct de Partenaire.objectifProduction (objectif individuel par partenaire).
     */
    @Column(name = "objectif_global", precision = 15, scale = 2)
    private BigDecimal objectifGlobal;

    @CreationTimestamp
    @Column(updatable = false)
    @JsonIgnore
    private Instant creation;

    @UpdateTimestamp
    @JsonIgnore
    private Instant lastUpdate;
}
