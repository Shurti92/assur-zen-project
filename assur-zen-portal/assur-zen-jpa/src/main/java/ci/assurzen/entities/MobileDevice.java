package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Appareil d'un compte mobile enregistre pour les notifications push (FCM), 2026-11-03. Un jeton = un appareil. */
@Entity
@Table(
        name = "t_assur_zen_mobile_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_device_token", columnNames = "token"),
        indexes = @Index(name = "idx_mobile_device_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class MobileDevice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    /** ANDROID ou IOS. */
    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}
