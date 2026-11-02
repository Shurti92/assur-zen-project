package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_enquete_satisfaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_enquete_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_enquete_insured", columnList = "insured_id"),
                @Index(name = "idx_enquete_contrat", columnList = "contrat_id"),
                @Index(name = "idx_enquete_sinistre", columnList = "sinistre_id"),
                @Index(name = "idx_enquete_statut", columnList = "statut"),
                @Index(name = "idx_enquete_contexte", columnList = "contexte")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnqueteSatisfaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "contexte", nullable = false, length = 30)
    private EnqueteContexte contexte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_enquete_insured"))
    private Insured insured;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_id", foreignKey = @ForeignKey(name = "fk_enquete_contrat"))
    private Contract contrat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinistre_id", foreignKey = @ForeignKey(name = "fk_enquete_sinistre"))
    private Sinistre sinistre;

    @NotNull
    @Column(name = "date_envoi", nullable = false)
    private Instant dateEnvoi;

    @Column(name = "date_reponse")
    private Instant dateReponse;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private EnqueteStatut statut;

    @Min(0)
    @Max(10)
    @Column(name = "score_nps")
    private Integer scoreNps;

    @Lob
    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
