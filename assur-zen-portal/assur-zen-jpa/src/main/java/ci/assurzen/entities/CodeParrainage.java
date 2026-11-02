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
 * Code de parrainage reutilisable genere pour un parrain dans un programme donne. Un meme code peut
 * etre utilise par plusieurs filleuls (jusqu'a maxUtilisations) - distinct de Parrainage, qui est cree
 * une fois par filleul utilisant ce code.
 */
@Entity
@Table(
        name = "t_assur_zen_code_parrainage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_code_parrainage_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_code_parrainage_parrain", columnList = "parrain_id"),
                @Index(name = "idx_code_parrainage_programme", columnList = "programme_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parrain_id", nullable = false, foreignKey = @ForeignKey(name = "fk_code_parrainage_parrain"))
    private Insured parrain;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programme_id", nullable = false, foreignKey = @ForeignKey(name = "fk_code_parrainage_programme"))
    private ProgrammeParrainage programme;

    @NotNull
    @Builder.Default
    @Column(name = "nombre_utilisations", nullable = false)
    private Integer nombreUtilisations = 0;

    /** null = illimite (ou herite du quota du programme, applique cote service). */
    @Column(name = "max_utilisations")
    private Integer maxUtilisations;

    @Column(name = "date_expiration")
    private Instant dateExpiration;

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
