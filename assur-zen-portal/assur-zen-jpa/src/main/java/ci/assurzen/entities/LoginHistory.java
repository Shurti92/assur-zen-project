package ci.assurzen.entities;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Une ligne par connexion reussie (login classique ou 2FA/OTP complete) - pas les tentatives
 * echouees, deja couvertes cote verrouillage de compte par LoginAttemptService. Immuable : aucun
 * endpoint de modification/suppression n'est expose (meme convention que HistoriqueModification).
 */
@Entity
@Table(
        name = "t_assur_zen_login_history",
        indexes = {
                @Index(name = "idx_login_history_user", columnList = "userId"),
                @Index(name = "idx_login_history_partenaire", columnList = "partenaireId"),
                @Index(name = "idx_login_history_created_at", columnList = "createdAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String username;

    /** Non-null uniquement si le compte est celui d'un partenaire (cf. AuthService.buildUserResponse). */
    private Long partenaireId;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeviceType deviceType;

    @Column(length = 100)
    private String browser;

    @Column(length = 100)
    private String os;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(length = 150)
    private String isp;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    /** Tentative ECHOUEE (mot de passe incorrect) - 2026-10-03, historique self-service de l'Espace
     * Partenaire. NULL = connexion reussie (toutes les lignes anterieures). */
    @Column(name = "echec")
    private Boolean echec;

    /** Motif de l'echec : MOT_DE_PASSE. NULL pour une connexion reussie. */
    @Column(name = "motif_echec", length = 30)
    private String motifEchec;

    /** Signalee "Ce n'etait pas moi" par l'utilisateur (POST /auth/me/login-history/{id}/report). */
    @Column(name = "signale_at")
    private Instant signaleAt;
}
