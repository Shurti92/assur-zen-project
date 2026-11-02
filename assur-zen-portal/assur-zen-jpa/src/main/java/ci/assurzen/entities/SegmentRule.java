package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;

/**
 * Semantique d'evaluation (voir SegmentRuleEvaluator) : les regles d'un meme "groupe" sont
 * combinees en ET, puis les groupes entre eux en OU (forme normale disjonctive, pattern standard
 * des moteurs de segmentation CRM). "operateurLogique" est conserve par fidelite au modele
 * demande mais n'est pas ce qui pilote l'evaluation (celle-ci suit strictement groupe=ET / OU
 * inter-groupes, plus previsible qu'un pliage sequentiel).
 */
@Entity
@Table(
        name = "t_assur_zen_communication_segment_rule",
        indexes = {
                @Index(name = "idx_segment_rule_segment", columnList = "segment_id"),
                @Index(name = "idx_segment_rule_groupe", columnList = "segment_id,groupe")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentRule extends AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_segment_rule_segment"))
    private Segment segment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, length = 30)
    private SegmentRuleField champ;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operateur", nullable = false, length = 30)
    private SegmentRuleOperator operateur;

    @Size(max = 500)
    @Column(name = "valeur", length = 500)
    private String valeur;

    @NotNull
    @Builder.Default
    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @NotNull
    @Builder.Default
    @Column(name = "groupe", nullable = false)
    private Integer groupe = 0;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "operateur_logique", nullable = false, length = 10)
    private LogicalOperator operateurLogique = LogicalOperator.AND;
}
