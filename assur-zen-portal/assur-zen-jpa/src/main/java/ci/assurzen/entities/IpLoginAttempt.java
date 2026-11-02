package ci.assurzen.entities;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/**
 * Compteur d'echecs de connexion par adresse IP (brute-force / credential stuffing), en parallele
 * du verrouillage par compte deja existant (User.failedLoginAttempts/lockedUntil). Une ligne par
 * IP ayant deja tente de se connecter au moins une fois.
 */
@Entity
@Table(
        name = "t_assur_zen_ip_login_attempt",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ip_login_attempt_ip", columnNames = "ipAddress")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpLoginAttempt implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String ipAddress;

    @Column(nullable = false)
    private int failedAttempts;

    private Instant blockedUntil;

    @Column(nullable = false)
    private Instant lastAttemptAt;
}
