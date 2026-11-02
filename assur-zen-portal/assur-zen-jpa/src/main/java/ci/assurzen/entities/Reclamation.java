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

@Entity
@Table(
        name = "t_assur_zen_reclamations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reclamation_numero", columnNames = "numero")
        },
        indexes = {
                @Index(name = "idx_reclamation_numero", columnList = "numero"),
                @Index(name = "idx_reclamation_insured", columnList = "insured_id"),
                @Index(name = "idx_reclamation_contrat", columnList = "contrat_id"),
                @Index(name = "idx_reclamation_statut", columnList = "statut"),
                @Index(name = "idx_reclamation_priorite", columnList = "priorite"),
                @Index(name = "idx_reclamation_type", columnList = "type"),
                @Index(name = "idx_reclamation_gestionnaire", columnList = "gestionnaire_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reclamation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reclamation_insured"))
    private Insured insured;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_id", foreignKey = @ForeignKey(name = "fk_reclamation_contrat"))
    private Contract contrat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", foreignKey = @ForeignKey(name = "fk_reclamation_produit"))
    private Produit produit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sinistre_id", foreignKey = @ForeignKey(name = "fk_reclamation_sinistre"))
    private Sinistre sinistre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enquete_id", foreignKey = @ForeignKey(name = "fk_reclamation_enquete"))
    private EnqueteSatisfaction enquete;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 30)
    private ReclamationCanal canal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ReclamationType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "priorite", nullable = false, length = 20)
    private ReclamationPriorite priorite = ReclamationPriorite.NORMALE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 30)
    private ReclamationStatut statut = ReclamationStatut.NOUVELLE;

    @NotBlank
    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestionnaire_id", foreignKey = @ForeignKey(name = "fk_reclamation_gestionnaire"))
    private User gestionnaire;

    @Size(max = 100)
    @Column(name = "service", length = 100)
    private String service;

    @Column(name = "date_affectation")
    private Instant dateAffectation;

    @Column(name = "date_resolution")
    private Instant dateResolution;

    @Column(name = "date_cloture")
    private Instant dateCloture;

    @Lob
    @Column(name = "motif_cloture", columnDefinition = "TEXT")
    private String motifCloture;

    @Column(name = "creee_automatiquement", nullable = false)
    @Builder.Default
    private boolean creeeAutomatiquement = false;

    /** Reflete le resultat du DERNIER sms envoye a l'assure sur cette reclamation (creation/affectation/statut/cloture). */
    @Column(name = "notification_envoyee")
    private Boolean notificationEnvoyee;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
