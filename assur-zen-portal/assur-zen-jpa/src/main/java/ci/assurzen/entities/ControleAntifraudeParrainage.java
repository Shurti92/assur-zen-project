package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Trace immuable d'un controle anti-fraude execute sur un parrainage (auto-parrainage, telephone/email
 * duplique, client deja existant, parrainages multiples...). Chaque decision du moteur est historisee ici,
 * meme quand elle ne bloque pas le parrainage (statut REUSSI), pour audit complet.
 */
@Entity
@Table(
        name = "t_assur_zen_controle_antifraude_parrainage",
        indexes = {
                @Index(name = "idx_controle_antifraude_parrainage_parrainage", columnList = "parrainage_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControleAntifraudeParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parrainage_id", nullable = false, foreignKey = @ForeignKey(name = "fk_controle_antifraude_parrainage_parrainage"))
    private Parrainage parrainage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_controle", nullable = false, length = 25)
    private TypeControleFraude typeControle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 15)
    private StatutControleFraude statut;

    @Column(name = "score")
    private Integer score;

    @Size(max = 500)
    @Column(name = "motif", length = 500)
    private String motif;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
