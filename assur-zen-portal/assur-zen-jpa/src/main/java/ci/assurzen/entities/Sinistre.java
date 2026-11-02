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
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_sinistres",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sinistre_numero", columnNames = "numero")
        },
        indexes = {
                @Index(name = "idx_sinistre_numero", columnList = "numero"),
                @Index(name = "idx_sinistre_contract", columnList = "contract_id"),
                @Index(name = "idx_sinistre_insured", columnList = "insured_id"),
                @Index(name = "idx_sinistre_status", columnList = "status"),
                @Index(name = "idx_sinistre_declarant", columnList = "declarant_id"),
                @Index(name = "idx_sinistre_assignee", columnList = "assignee_id"),
                @Index(name = "idx_sinistre_compagnie", columnList = "compagnie_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sinistre implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "numero", nullable = false, unique = true, length = 50)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_contract"))
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_insured"))
    private Insured insured;

    @NotBlank
    @Size(max = 200)
    @Column(name = "nature", nullable = false, length = 200)
    private String nature;

    @NotNull
    @Column(name = "date_survenance", nullable = false)
    private LocalDate dateSurvenance;

    @NotNull
    @Column(name = "date_declaration", nullable = false)
    private LocalDate dateDeclaration;

    @Column(name = "montant_estime")
    private Long montantEstime;

    @Column(name = "montant_indemnise")
    private Long montantIndemnise;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SinistreStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declarant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sinistre_declarant"))
    private User declarant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", foreignKey = @ForeignKey(name = "fk_sinistre_assignee"))
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compagnie_id", foreignKey = @ForeignKey(name = "fk_sinistre_compagnie"))
    private Compagnie compagnie;

    @Size(max = 200)
    @Column(name = "gestionnaire_nom", length = 200)
    private String gestionnaireNom;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
