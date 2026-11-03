package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Demande de modification ou de resiliation d'un contrat faite par l'assure depuis le mobile (2026-11-02). */
@Entity
@Table(
        name = "t_assur_zen_contract_customer_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_contract_customer_request_reference", columnNames = "reference"),
        indexes = {
                @Index(name = "idx_contract_customer_request_contract", columnList = "contract_id"),
                @Index(name = "idx_contract_customer_request_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ContractCustomerRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** MODIFICATION ou TERMINATION. */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "message", length = 1000)
    private String message;

    /** EN_ATTENTE, TRAITEE ou REFUSEE. */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
