package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Une condition parmi celles d'une CrossSellRule ; toutes les conditions d'une regle sont combinees en ET. */
@Entity
@Table(
        name = "t_assur_zen_cross_sell_rule_condition",
        indexes = {
                @Index(name = "idx_cross_sell_condition_rule", columnList = "regle_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossSellRuleCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cross_sell_condition_regle"))
    private CrossSellRule regle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, length = 30)
    private CrossSellChamp champ;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operateur", nullable = false, length = 30)
    private SegmentRuleOperator operateur;

    @NotBlank
    @Size(max = 100)
    @Column(name = "valeur", nullable = false, length = 100)
    private String valeur;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
