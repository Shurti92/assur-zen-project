package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_communication_segment_membre",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_segment_membre", columnNames = {"segment_id", "insured_id"})
        },
        indexes = {
                @Index(name = "idx_segment_membre_segment", columnList = "segment_id"),
                @Index(name = "idx_segment_membre_insured", columnList = "insured_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentMembre implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_segment_membre_segment"))
    private Segment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_segment_membre_insured"))
    private Insured insured;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
