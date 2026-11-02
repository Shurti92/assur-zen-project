package ci.assurzen.entities;

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
 * Audit trail FONCTIONNEL (qui/quoi/quand/avant-apres, lisible metier) - complementaire a AuditFlux
 * (audit TECHNIQUE : requete/reponse HTTP brute), pas un remplacement. Relie a AuditFlux via
 * correlationId (le meme code UUID que AuditLoggingFilter genere pour la requete HTTP porteuse).
 * Immuable par conception : aucun UPDATE/DELETE n'est expose via l'API, donc pas de last_update ni
 * de @Version (rien ne modifie jamais une ligne apres son insertion).
 */
@Entity
@Table(
        name = "t_assur_zen_historique_modification",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_historique_modification_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_historique_modification_created_at", columnList = "created_at"),
                @Index(name = "idx_historique_modification_user", columnList = "user_id"),
                @Index(name = "idx_historique_modification_module", columnList = "module"),
                @Index(name = "idx_historique_modification_entite", columnList = "entity_name, entity_id"),
                @Index(name = "idx_historique_modification_action", columnList = "action"),
                @Index(name = "idx_historique_modification_correlation", columnList = "correlation_id"),
                @Index(name = "idx_historique_modification_statut", columnList = "statut")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueModification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    /** Code UUID partage avec AuditFlux (meme requete HTTP) - null pour les actions declenchees hors requete (SYSTEM/SCHEDULER). */
    @Size(max = 40)
    @Column(name = "correlation_id", length = 40)
    private String correlationId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @NotBlank
    @Size(max = 100)
    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Size(max = 50)
    @Column(name = "entity_id", length = 50)
    private String entityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ActionAudit action;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 500)
    @Column(name = "motif", length = 500)
    private String motif;

    /** JSON : uniquement les champs reellement modifies (UPDATE), valeurs sensibles masquees. */
    @Lob
    @Column(name = "ancienne_valeur", columnDefinition = "TEXT")
    private String ancienneValeur;

    @Lob
    @Column(name = "nouvelle_valeur", columnDefinition = "TEXT")
    private String nouvelleValeur;

    /** JSON array des noms de champs modifies, ex. ["premium","status"]. */
    @Lob
    @Column(name = "champs_modifies", columnDefinition = "TEXT")
    private String champsModifies;

    @Column(name = "user_id")
    private Long userId;

    @Size(max = 100)
    @Column(name = "username", length = 100)
    private String username;

    @Size(max = 150)
    @Column(name = "user_role", length = 150)
    private String userRole;

    @Size(max = 45)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "canal", nullable = false, length = 15)
    private CanalAudit canal = CanalAudit.SYSTEM;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 10)
    private StatutAudit statut = StatutAudit.SUCCES;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
