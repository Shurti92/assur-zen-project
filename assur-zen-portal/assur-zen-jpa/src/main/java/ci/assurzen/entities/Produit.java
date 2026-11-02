package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_produit"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 200)
    @Column(name = "nom_produit", nullable = false, length = 200)
    private String nomProduit;

    /**
     * Nullable au niveau schema (migration sans risque sur les donnees existantes, cf. lecon
     * ddl-auto qui ne resserre jamais une contrainte) : en pratique toujours renseigne, via le
     * backfill de migration ou le fallback "AUTRES" applique par ProduitService a la creation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id", foreignKey = @ForeignKey(name = "fk_produit_product_type"))
    private ProductType productType;

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
