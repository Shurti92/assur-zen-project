package ci.assurzen.webpush.entity;

import ci.assurzen.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Abonnement Web Push d'un navigateur/appareil (PushSubscription du frontend EP, 2026-09-26). Un
 * utilisateur peut en avoir plusieurs (un par navigateur/appareil). endpoint_hash (SHA-256 de
 * l'endpoint) porte l'unicite : MySQL n'accepte pas d'index unique sur une colonne TEXT.
 */
@Entity
@Table(
        name = "t_assur_zen_web_push_subscription",
        uniqueConstraints = @UniqueConstraint(name = "uk_wps_endpoint_hash", columnNames = "endpoint_hash"),
        indexes = @Index(name = "idx_wps_user_active", columnList = "user_id, active")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebPushSubscription implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wps_user"))
    private User user;

    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64)
    private String endpointHash;

    /** Cle publique ECDH du navigateur (base64url) - pas un secret de l'application. */
    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 64)
    private String auth;

    @Column(name = "expiration_time")
    private Long expirationTime;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
