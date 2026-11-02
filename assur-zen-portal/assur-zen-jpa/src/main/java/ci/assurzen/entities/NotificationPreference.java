package ci.assurzen.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Preference de notification d'un utilisateur pour une categorie (2026-10-03). Absence de ligne = tout
 * active (comportement historique). La notification in-app est toujours creee : seuls le push et le SMS
 * se reglent, et le SMS seulement quand la categorie le permet (NotificationCategorie.smsModifiable).
 */
@Entity
@Table(
        name = "t_assur_zen_notification_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_preference", columnNames = {"user_id", "categorie"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Chaine (pas d'ENUM SQL) : une nouvelle categorie ne demande aucun script de migration. */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 40, columnDefinition = "varchar(40)")
    private NotificationCategorie categorie;

    @Column(name = "push", nullable = false)
    private boolean push;

    @Column(name = "sms", nullable = false)
    private boolean sms;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
