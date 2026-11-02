package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
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
 * Un parrainage : instance concrete d'un parrain recommandant un filleul, suivie a travers son cycle
 * de vie complet (CREE -> ... -> RECOMPENSE_ATTRIBUEE, ou REJETE/ANNULE/EXPIRE). Le filleul peut n'etre
 * connu que par email/telephone au depart (avant inscription effective en tant qu'Insured).
 */
@Entity
@Table(
        name = "t_assur_zen_parrainage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_parrainage_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_parrainage_parrain", columnList = "parrain_id"),
                @Index(name = "idx_parrainage_filleul", columnList = "filleul_id"),
                @Index(name = "idx_parrainage_statut", columnList = "statut"),
                @Index(name = "idx_parrainage_programme", columnList = "programme_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Parrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programme_id", nullable = false, foreignKey = @ForeignKey(name = "fk_parrainage_programme"))
    private ProgrammeParrainage programme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_parrainage_id", foreignKey = @ForeignKey(name = "fk_parrainage_code"))
    private CodeParrainage codeParrainage;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parrain_id", nullable = false, foreignKey = @ForeignKey(name = "fk_parrainage_parrain"))
    private Insured parrain;

    /** Renseigne une fois que le filleul s'est effectivement inscrit comme assure. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filleul_id", foreignKey = @ForeignKey(name = "fk_parrainage_filleul"))
    private Insured filleul;

    @Size(max = 150)
    @Column(name = "filleul_nom", length = 150)
    private String filleulNom;

    @Email
    @Size(max = 150)
    @Column(name = "filleul_email", length = 150)
    private String filleulEmail;

    @Size(max = 30)
    @Column(name = "filleul_telephone", length = 30)
    private String filleulTelephone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_id", foreignKey = @ForeignKey(name = "fk_parrainage_contrat"))
    private Contract contrat;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 25)
    private StatutParrainage statut = StatutParrainage.CREE;

    @Lob
    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    private String motifRejet;

    @Column(name = "date_invitation")
    private Instant dateInvitation;

    @Column(name = "date_inscription")
    private Instant dateInscription;

    @Column(name = "date_eligibilite")
    private Instant dateEligibilite;

    @Column(name = "date_completion")
    private Instant dateCompletion;

    @Column(name = "date_annulation")
    private Instant dateAnnulation;

    @Column(name = "date_expiration")
    private Instant dateExpiration;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
