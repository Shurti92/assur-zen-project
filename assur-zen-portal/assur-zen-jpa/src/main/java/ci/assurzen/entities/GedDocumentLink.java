package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Rattachement generique d'un GedDocument a un objet metier (client/contrat/vehicule/flotte/
 * sinistre/reclamation...). entityType est un texte libre (pas un enum ferme), meme choix que
 * PartenaireDocument.type : permet d'ajouter de nouveaux modules sans modifier la structure de la
 * GED. Une seule table + un index (entity_type, entity_id) sert a la fois les recherches "documents
 * d'un client/contrat/vehicule/flotte" - pas une table dediee par module.
 */
@Entity
@Table(
        name = "t_assur_zen_ged_document_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ged_document_link", columnNames = {"document_id", "entity_type", "entity_id"})
        },
        indexes = {
                @Index(name = "idx_ged_document_link_document", columnList = "document_id"),
                @Index(name = "idx_ged_document_link_entity", columnList = "entity_type, entity_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GedDocumentLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ged_document_link_document"))
    private GedDocument document;

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Size(max = 50)
    @Column(name = "relationship_type", length = 50)
    private String relationshipType;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
