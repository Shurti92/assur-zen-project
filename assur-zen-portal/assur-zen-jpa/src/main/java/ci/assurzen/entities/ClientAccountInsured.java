package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Rattachement d'un compte mobile (User, rôle CLIENT) à un assuré du back-office (2026-11-02). */
@Entity
@Table(
        name = "t_assur_zen_client_account_insured",
        uniqueConstraints = @UniqueConstraint(name = "uk_client_account_insured", columnNames = {"user_id", "insured_id"}),
        indexes = @Index(name = "idx_client_account_insured_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class ClientAccountInsured implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "insured_id", nullable = false)
    private Long insuredId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt = Instant.now();

    public ClientAccountInsured(Long userId, Long insuredId) {
        this.userId = userId;
        this.insuredId = insuredId;
    }
}
