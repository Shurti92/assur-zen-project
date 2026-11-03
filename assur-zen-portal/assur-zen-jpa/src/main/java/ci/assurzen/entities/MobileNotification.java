package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Notification in-app d'un compte mobile (2026-11-03). Conservee NOTIFICATION_RETENTION_MONTHS mois (defaut 12). */
@Entity
@Table(name = "t_assur_zen_mobile_notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_notification_dedupe", columnNames = {"user_id", "dedupe_key"}),
        indexes = @Index(name = "idx_mobile_notification_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class MobileNotification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** EXPIRY, SUBSCRIPTION, PAYMENT, CLAIM, DOCUMENT ou SERVICE. */
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "claim_id")
    private Long claimId;

    /** Evite les doublons d'un meme evenement (ex. echeance J-30 d'un contrat). */
    @Column(name = "dedupe_key", length = 100)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;
}
