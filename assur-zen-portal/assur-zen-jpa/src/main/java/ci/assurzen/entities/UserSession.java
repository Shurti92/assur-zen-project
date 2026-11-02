package ci.assurzen.entities;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "t_assur_zen_user_session",
        indexes = {
                @Index(name = "idx_user_session_token", columnList = "token"),
                @Index(name = "idx_user_session_user", columnList = "userId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    // ---- Appareils connectes (2026-10-03) : le refresh token tourne a chaque renouvellement (une ligne
    // par jeton), sessionId reste le meme de la connexion jusqu'a la deconnexion. NULL pour les lignes
    // anterieures. ----

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20, columnDefinition = "varchar(20)")
    private DeviceType deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    /** Premiere connexion de la session (conservee d'un renouvellement a l'autre). */
    @Column(name = "connected_at")
    private Instant connectedAt;

    /** Dernier renouvellement du jeton (= derniere activite connue). */
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /** Dernier access token emis pour cette session : revoque immediatement quand l'utilisateur
     * deconnecte la session depuis un autre appareil. */
    @Column(name = "access_jti", length = 64)
    private String accessJti;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    /** Date a laquelle ce refresh token a ete CONSOMME par un renouvellement (rotation) - distinct d'une
     * revocation par deconnexion. Sert a tolerer, quelques secondes, un renouvellement concurrent du meme
     * jeton (cf. AuthService.refresh). */
    @Column(name = "rotated_at")
    private Instant rotatedAt;
}
