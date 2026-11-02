package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Avantage effectivement accorde a un compte fidelite (auto-attribue par palier, ou manuel). */
@Entity
@Table(
        name = "t_assur_zen_compte_fidelite_avantage",
        indexes = {
                @Index(name = "idx_compte_avantage_compte", columnList = "compte_fidelite_id"),
                @Index(name = "idx_compte_avantage_avantage", columnList = "avantage_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompteFideliteAvantage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compte_fidelite_id", nullable = false, foreignKey = @ForeignKey(name = "fk_compte_avantage_compte"))
    private CompteFidelite compteFidelite;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avantage_id", nullable = false, foreignKey = @ForeignKey(name = "fk_compte_avantage_avantage"))
    private Avantage avantage;

    @NotNull
    @Column(name = "date_attribution", nullable = false)
    private Instant dateAttribution;

    @Column(name = "date_expiration")
    private Instant dateExpiration;

    @Builder.Default
    @Column(nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
