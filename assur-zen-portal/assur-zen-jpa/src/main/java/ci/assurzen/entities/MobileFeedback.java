package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Avis (etoiles) d'un client apres souscription ou service, depuis le mobile (2026-11-03). */
@Entity
@Table(name = "t_assur_zen_mobile_feedback",
        indexes = @Index(name = "idx_mobile_feedback_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class MobileFeedback implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contract_id")
    private Long contractId;

    /** 1 a 5. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
