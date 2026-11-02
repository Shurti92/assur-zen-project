package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_produit_commission_taux",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_produit_type_partenaire", columnNames = {"produit_id", "type_partenaire"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProduitCommissionTaux implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_produit_commission_taux_produit"))
    private Produit produit;


    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_partenaire", nullable = false, length = 20)
    private PartenaireType typePartenaire;


    @NotNull
    @DecimalMin(value = "0.0000")
    @Digits(integer = 10, fraction = 4)
    @Column(name = "taux_commission", nullable = false, precision = 14, scale = 4)
    private BigDecimal tauxCommission;


    @NotBlank
    @Size(max = 10)
    @Column(name = "lib_taux_commission", nullable = false, length = 10)
    private String libTauxCommission;


    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonIgnore
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;
}
