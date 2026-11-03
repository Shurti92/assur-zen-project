package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Demande d'aide d'un client depuis le mobile (2026-11-03), avec la reponse du conseiller. */
@Entity
@Table(name = "t_assur_zen_support_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_support_request_reference", columnNames = "reference"),
        indexes = @Index(name = "idx_support_request_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SupportRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "topic", nullable = false, length = 40)
    private String topic;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    /** EN_ATTENTE, EN_COURS, REPONDU ou CLOTURE. */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "response", length = 2000)
    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "responded_by", length = 100)
    private String respondedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
