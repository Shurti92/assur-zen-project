package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_partenaire",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_partenaire_code", columnNames = "code_partenaire")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partenaire implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 20)
    @Column(name = "code_partenaire", nullable = false, unique = true, length = 20)
    private String codePartenaire;


    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PartenaireType type;


    @NotBlank
    @Size(max = 50)
    @Column(name = "nom", nullable = false, length = 50)
    private String nom;


    @NotBlank
    @Size(max = 200)
    @Column(name = "prenoms", nullable = false, length = 200)
    private String prenoms;


    @DecimalMin(value = "0.00")
    @Digits(integer = 13, fraction = 2)
    @Column(name = "objectif_production", precision = 15, scale = 2)
    private BigDecimal objectifProduction;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profiles_assur_zen_partenaire")
    )
    private Profile profile;


    /**
     * Determine si ce partenaire declenche la creation automatique d'un compte de connexion
     * (TypeProfile.declencheCreationCompte=true, typiquement "REPRESENTANT") - distinct du champ
     * "type" existant (DIRECT/AGENT/APPORTEUR), les deux coexistent sans se recouvrir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "type_profile_id",
            foreignKey = @ForeignKey(name = "fk_partenaire_type_profile")
    )
    private TypeProfile typeProfile;


    /** Numero de connexion (username du compte cree automatiquement) - absent avant ce champ. Meme longueur que Profile.phone. */
    @Size(max = 20)
    @Column(name = "telephone", length = 20)
    private String telephone;


    /** Numero Wave pour le versement des commissions - distinct de telephone (canal exclusivement Wave, regle metier fixe). */
    @Size(max = 20)
    @Column(name = "numero_wave_commission", length = 20)
    private String numeroWaveCommission;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "exercice_comptable_id",
            foreignKey = @ForeignKey(name = "fk_partenaire_exercice_comptable")
    )
    private ExerciceComptable exerciceComptable;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ville_partenaire_id",
            foreignKey = @ForeignKey(name = "fk_partenaire_ville_partenaire")
    )
    private VillePartenaire villePartenaire;


    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;


    /** Purement informatif (cf. contrat "Frequence de paiement de commission" §1.3) : aucune automatisation
     * serveur ne se declenche sur ce champ tant qu'aucune API de paiement de commission n'existe. */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequence_paiement_commission", length = 20)
    private FrequencePaiementCommission frequencePaiementCommission;


    /**
     * Deuxieme condition (ET logique avec TypeProfile.declencheCreationCompte) pour la creation/activation
     * automatique du compte de connexion - cf. contrat "Frequence de paiement de commission" §2. Default
     * DB=true (ColumnDefault) uniquement pour retro-compatibilite : backfill des partenaires deja en base
     * a true pour ne pas declencher une desactivation en cascade de comptes deja actifs a leur prochaine
     * modification. Les NOUVEAUX partenaires suivent la regle du contrat (absent = false), geree cote
     * PartenaireService, pas par ce defaut (Hibernate specifie toujours la valeur explicitement a l'insert).
     */
    @Builder.Default
    @ColumnDefault("1")
    @Column(name = "acces_activite_commerciale", nullable = false)
    private boolean accesActiviteCommerciale = false;


    /** Active/desactive la prime d'objectif annuelle pour ce partenaire (contrat "Prime d'objectif
     * partenaire", 2026-08-28) - defaut false, ne concerne QUE ce partenaire (pas une bascule globale). */
    @Builder.Default
    @Column(name = "prime_objectif_activee", nullable = false)
    private boolean primeObjectifActivee = false;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonIgnore
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;
}
