package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Preference d'un compte mobile pour les notifications PUSH d'une categorie (2026-11-03) ; sans ligne = activee. */
@Entity
@Table(
        name = "t_assur_zen_mobile_notification_pref",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_notification_pref", columnNames = {"user_id", "category"})
)
@Getter
@Setter
@NoArgsConstructor
public class MobileNotificationPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
