package ci.assurzen.entities;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Un hash bcrypt par ancien mot de passe, pour empecher sa reutilisation (PasswordHistoryService).
 * Jamais le mot de passe en clair. Immuable : aucun endpoint de lecture/modification n'est expose,
 * consulte uniquement en interne par PasswordHistoryService.
 */
@Entity
@Table(
        name = "t_assur_zen_password_history",
        indexes = {
                @Index(name = "idx_password_history_user", columnList = "userId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;
}
