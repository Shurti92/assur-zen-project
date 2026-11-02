package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Regle configurable du moteur de score de fidelite : chaque regle active, si elle "matche" pour un
 * assure donne, ajoute son poids au score total. Aucun seuil/ponderation n'est code en dur en Java —
 * tout vient de cette table (voir ScoreFideliteService). Meme esprit que SegmentRule (Segments/
 * Templates) mais le resultat d'une regle est une pondaration cumulee, pas une inclusion booleenne.
 */
@Entity
@Table(
        name = "t_assur_zen_regle_fidelite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_regle_fidelite_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_regle_fidelite_actif", columnList = "actif")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegleFidelite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, length = 30)
    private RegleFideliteChamp champ;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operateur", nullable = false, length = 30)
    private SegmentRuleOperator operateur;

    @NotBlank
    @Size(max = 100)
    @Column(name = "valeur", nullable = false, length = 100)
    private String valeur;

    @NotNull
    @Column(name = "poids", nullable = false)
    private Long poids;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
