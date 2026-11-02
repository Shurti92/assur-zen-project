package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_campagne_destinataire",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_destinataire_campagne_insured_canal", columnNames = {"campagne_id", "insured_id", "canal"})
        },
        indexes = {
                @Index(name = "idx_destinataire_campagne", columnList = "campagne_id"),
                @Index(name = "idx_destinataire_insured", columnList = "insured_id"),
                @Index(name = "idx_destinataire_statut", columnList = "statut_envoi")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampagneDestinataire implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campagne_id", nullable = false, foreignKey = @ForeignKey(name = "fk_destinataire_campagne"))
    private CampagneMarketing campagne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_destinataire_insured"))
    private Insured insured;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalEnvoi canal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_envoi", nullable = false, length = 20)
    private StatutEnvoiDestinataire statutEnvoi;

    @Column(name = "date_envoi")
    private Instant dateEnvoi;

    @Size(max = 500)
    @Column(name = "erreur_message", length = 500)
    private String erreurMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
