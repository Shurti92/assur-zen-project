package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Demande de rappel faite depuis l'application mobile, avec ou sans compte (2026-11-03). */
@Entity
@Table(
        name = "t_assur_zen_callback_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_callback_request_reference", columnNames = "reference"),
        indexes = {
                @Index(name = "idx_callback_request_phone", columnList = "phone"),
                @Index(name = "idx_callback_request_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CallbackRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    /** ASAP, MORNING, AFTERNOON ou EVENING. */
    @Column(name = "slot", nullable = false, length = 20)
    private String slot;

    @Column(name = "product_id", length = 60)
    private String productId;

    /** Compte mobile connecte, null pour une demande faite avant l'inscription. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** EN_ATTENTE, RAPPELE ou ANNULE. */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    /** Administrateur qui a traite la demande et son commentaire (2026-11-03). */
    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "process_comment", length = 500)
    private String processComment;
}
