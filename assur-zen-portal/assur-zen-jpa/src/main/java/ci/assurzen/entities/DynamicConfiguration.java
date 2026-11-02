package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Parametre metier/technique modifiable en base sans redeploiement (Dynamic Configuration Engine).
 * configValue est stocke CHIFFRE quand sensitive=true (via ConfigurationEncryptionService, reutilise
 * spring-security-crypto deja present dans le projet) - jamais en clair pour les cles
 * password/secret/token/api_key.
 */
@Entity
@Table(
        name = "t_assur_zen_dynamic_configuration",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dynamic_configuration_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_dynamic_configuration_key", columnNames = "config_key")
        },
        indexes = {
                @Index(name = "idx_dynamic_configuration_module", columnList = "module"),
                @Index(name = "idx_dynamic_configuration_category", columnList = "category"),
                @Index(name = "idx_dynamic_configuration_active", columnList = "active")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicConfiguration extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Builder.Default
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    private String uuid = UUID.randomUUID().toString();

    @NotBlank
    @Size(max = 100)
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /** Chiffre (AES via ConfigurationEncryptionService) quand sensitive=true, en clair sinon. */
    @Lob
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 15)
    private ConfigValueType valueType;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 50)
    @Column(name = "module", length = 50)
    private String module;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "category", nullable = false, length = 20)
    private ConfigCategory category = ConfigCategory.GENERAL;

    /** null = s'applique a tous les environnements. */
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 15)
    private ConfigEnvironment environment;

    @Lob
    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    /** Format libre combinable par ';' : "MIN:0;MAX:1000000", "REGEX:^[A-Z]+$", "ENUM:A,B,C". */
    @Size(max = 300)
    @Column(name = "validation_rule", length = 300)
    private String validationRule;

    /** Valeurs autorisees pour LIST/ENUM, separees par des virgules (affichage/validation cote UI). */
    @Lob
    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** false = lecture seule via l'API meme pour un admin (ex. valeur fixee au provisionnement). */
    @Builder.Default
    @Column(name = "editable", nullable = false)
    private boolean editable = true;

    /**
     * true = configValue chiffre en base et jamais retourne en clair par l'API (masque "********").
     * Colonne nommee is_sensitive (pas "sensitive") : SENSITIVE est un mot reserve MySQL/MariaDB
     * (utilise pour la sensibilite des curseurs dans les procedures stockees), le DDL echouait avec
     * "sensitive" tel quel.
     */
    @Builder.Default
    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive = false;

    /** Indicatif uniquement : signale a l'IU qu'un changement ne prendra reellement effet qu'apres redemarrage. */
    @Builder.Default
    @Column(name = "restart_required", nullable = false)
    private boolean restartRequired = false;

    @Builder.Default
    @Column(name = "cache_enabled", nullable = false)
    private boolean cacheEnabled = true;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
