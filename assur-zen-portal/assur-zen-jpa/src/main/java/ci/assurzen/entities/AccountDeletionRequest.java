package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Demande de suppression de compte faite depuis le mobile (2026-11-02), traitée par un administrateur. */
@Entity
@Table(
        name = "t_assur_zen_account_deletion_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_deletion_reference", columnNames = "reference"),
        indexes = @Index(name = "idx_account_deletion_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class AccountDeletionRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** EN_ATTENTE, TRAITEE ou REFUSEE. */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
