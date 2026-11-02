package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

/**
 * Une ligne (mouvement debit ou credit) d'une ecriture comptable. Par convention, exactement un des
 * deux montants (montantDebit / montantCredit) est non nul sur une ligne donnee ; l'autre vaut 0.
 */
@Entity
@Table(
        name = "t_assur_zen_ligne_ecriture",
        indexes = {
                @Index(name = "idx_ligne_ecriture_compte", columnList = "compte_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneEcriture implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecriture_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ligne_ecriture_ecriture"))
    @JsonIgnore
    private EcritureComptable ecriture;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compte_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ligne_ecriture_compte"))
    private CompteComptable compte;

    @NotNull
    @Builder.Default
    @Column(name = "montant_debit", nullable = false)
    private Long montantDebit = 0L;

    @NotNull
    @Builder.Default
    @Column(name = "montant_credit", nullable = false)
    private Long montantCredit = 0L;

    @Size(max = 255)
    @Column(name = "libelle", length = 255)
    private String libelle;
}
