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
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_campagne_marketing",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_campagne_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_campagne_statut", columnList = "statut"),
                @Index(name = "idx_campagne_dates", columnList = "date_debut,date_fin"),
                @Index(name = "idx_campagne_created_by", columnList = "created_by"),
                @Index(name = "idx_campagne_segment", columnList = "segment_id"),
                @Index(name = "idx_campagne_template", columnList = "template_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampagneMarketing implements Serializable {

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

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CampagneType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private CampagneStatut statut;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "cible", nullable = false, length = 30)
    private CampagneCible cible;

    /**
     * Nullable depuis l'ajout du template reutilisable : soit messageContenu est renseigne en
     * texte libre (comportement d'origine), soit un template est associe et c'est lui qui fournit
     * le contenu (avec substitution de variables) — l'un des deux est obligatoire, valide en
     * service plutot qu'ici (regle inter-champs, pas exprimable par @NotBlank seul).
     */
    @Lob
    @Column(name = "message_contenu", columnDefinition = "TEXT")
    private String messageContenu;

    @Size(max = 200)
    @Column(name = "sujet_email", length = 200)
    private String sujetEmail;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "budget_prevu")
    private Long budgetPrevu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_campagne_created_by"))
    private User createdBy;

    /** Ciblage dynamique — requis si cible = SEGMENT_DYNAMIQUE, ignore sinon. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", foreignKey = @ForeignKey(name = "fk_campagne_segment"))
    private Segment segment;

    /** Si renseigne, prevaut sur messageContenu/sujetEmail (resolus depuis le template + variables). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_campagne_template"))
    private CommunicationTemplate template;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
