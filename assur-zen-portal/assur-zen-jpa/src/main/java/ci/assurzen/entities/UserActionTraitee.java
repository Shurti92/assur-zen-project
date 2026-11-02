package ci.assurzen.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Action de la liste "A traiter" de l'Espace Partenaire marquee comme traitee par un utilisateur
 * (2026-10-03). La liste elle-meme est calculee cote front (echeances, primes a reverser, pieces
 * expirees...) ; le backend ne fait que memoriser les cles traitees, propres a chaque utilisateur.
 * Cle libre choisie par le front, ex. "ECHEANCE:{contractId}:{dateEcheance}".
 */
@Entity
@Table(
        name = "t_assur_zen_user_action_traitee",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_action_traitee", columnNames = {"user_id", "cle"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionTraitee implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cle", nullable = false, length = 150)
    private String cle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
