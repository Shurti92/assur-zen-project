package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_contracts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contract_policy_number", columnNames = "policy_number")
        },
        indexes = {
                @Index(name = "idx_contract_policy_number", columnList = "policy_number"),
                @Index(name = "idx_contract_sales_rep", columnList = "sales_rep_id"),
                @Index(name = "idx_contract_status", columnList = "status"),
                @Index(name = "idx_contract_commission_status", columnList = "commission_status"),
                @Index(name = "idx_contract_insured", columnList = "insured_id"),
                @Index(name = "idx_contract_partenaire", columnList = "partenaire_id"),
                @Index(name = "idx_contract_compagnie", columnList = "compagnie_id"),
                @Index(name = "idx_contract_vehicule", columnList = "vehicule_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "policy_number", nullable = false, unique = true, length = 50)
    private String policyNumber;



    @Column(name = "date_effet")
    private LocalDate dateEffet;


    @NotBlank
    @Size(max = 10)
    @Column(name = "duree", nullable = false, length = 10)
    private String duree;


    @Column(name = "date_echeance")
    private LocalDate dateEcheance;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contract_product_id"))
    private Produit product;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insured_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contract_insured"))
    private Insured insured;


    /** Vehicule couvert par ce contrat, le cas echeant (contrats auto/moto) - nullable, ajoute pour
     * le module Gestion de flotte. La flotte d'un contrat se deduit via
     * vehicule -> FleetVehicle(ACTIVE) -> Fleet, jamais stockee directement ici, pour ne pas
     * desynchroniser lors d'un transfert de vehicule entre flottes.
     * @NotFound(IGNORE) (2026-09-13, meme correctif que FleetVehicle/FleetMovement) : un vehicule
     * supprime (sans contrat lie a l'epoque de la suppression, cf. VehiculeService.delete()) ne doit
     * jamais faire planter la consultation d'un contrat plus ancien qui le referencait encore. */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "vehicule_id", foreignKey = @ForeignKey(name = "fk_contract_vehicule"))
    private Vehicule vehicule;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", foreignKey = @ForeignKey(name = "fk_contract_partenaire"))
    private Partenaire partenaire;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compagnie_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contract_compagnie"))
    private Compagnie compagnie;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_comptable_id", foreignKey = @ForeignKey(name = "fk_contract_exercice_comptable"))
    private ExerciceComptable exerciceComptable;


    @NotNull
    @Column(name = "premium", nullable = false)
    private Long premium;

    @NotNull
    @Column(name = "commission_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal commissionRate;

    @NotNull
    @Column(name = "commission_amount", nullable = false)
    private Long commissionAmount;

    @NotNull
    @Column(name = "partner_share", nullable = false)
    private Long partnerShare;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;


    @Enumerated(EnumType.STRING)
    @Column(name = "commission_status", nullable = false, length = 20)
    private CommissionStatus commissionStatus;


    /**
     * Suivi du reversement de la prime encaissee par le partenaire aupres du client, a la compagnie
     * (sens inverse de commissionStatus). Non pertinent si le contrat n'a pas de partenaire, mais
     * toujours renseigne par simplicite (meme convention que commissionStatus/partnerShare, deja a 0
     * pour les contrats sans partenaire). Decision produit du 2026-08-22 : bascule uniquement via
     * validation manuelle admin/finance, aucun delai de grace.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @ColumnDefault("'non_reverse'")
    @Column(name = "prime_reversement_status", nullable = false, length = 20)
    private StatutReversementPrime primeReversementStatus = StatutReversementPrime.non_reverse;


    @Column(name = "signed_at")
    private LocalDate signedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contract_sales_rep"))
    private User salesRep;


    /**
     * Commission versee au commercial interne (salesRep) qui a vendu ce contrat, en plus de son
     * salaire+bonus mensuel (contrat "Commission commerciale interne", 2026-09-08). Resolue
     * automatiquement depuis ProduitCommissionCommercial UNIQUEMENT quand le contrat n'a pas de
     * partenaire (partenaire == null) - vente via partenaire : la commission revient au partenaire
     * (partnerShare), pas au commercial qui l'a simplement traite techniquement. Null/0 sinon (meme
     * convention que partnerShare=0 pour une vente directe sans partenaire).
     */
    @Column(name = "commercial_commission_rate", precision = 8, scale = 4)
    private BigDecimal commercialCommissionRate;

    @NotNull
    @Builder.Default
    @Column(name = "commercial_commission_amount", nullable = false)
    private Long commercialCommissionAmount = 0L;

    /**
     * Bug reel du 2026-09-08 (corrige) : @Builder.Default est une pure construction Lombok cote Java,
     * sans aucun effet sur le DDL genere - sans @ColumnDefault explicite, l'ALTER TABLE ADD COLUMN
     * genere par Hibernate (mappe en ENUM MySQL natif pour @Enumerated(STRING), pas VARCHAR) laisse
     * MySQL appliquer SON PROPRE defaut implicite pour une colonne ENUM NOT NULL sans DEFAULT : la
     * PREMIERE valeur declaree du type - ici "paid" (CommissionStatus.paid est declare avant .pending),
     * pas "pending" comme attendu. Resultat : tous les contrats preexistant a cette colonne se sont
     * retrouves avec commercial_commission_status='paid' au lieu de 'pending' (corrige par backfill SQL,
     * cf. docs/api-contracts). @ColumnDefault fixe la valeur par defaut au niveau SQL et evite la
     * recidive sur tout futur ajout de colonne NOT NULL similaire via ddl-auto: update.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @ColumnDefault("'pending'")
    @Column(name = "commercial_commission_status", nullable = false, length = 20)
    private CommissionStatus commercialCommissionStatus = CommissionStatus.pending;


    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "mode_paiement", length = 20)
    private ModePaiement modePaiement = ModePaiement.COMPTANT;

    @Column(name = "nombre_echeances")
    private Integer nombreEcheances;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicite_echeances", length = 20)
    private PeriodiciteEcheance periodiciteEcheances;

    /**
     * Montant deja recu avant la generation de l'echeancier (mode FRACTIONNE uniquement) — deduit de
     * premium avant repartition dans EcheancierService.genererEcheancier(). Null/0 = rien de verse
     * d'avance, comportement inchange.
     */
    @Column(name = "montant_deja_verse")
    private Long montantDejaVerse;


    @Lob
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;


    /**
     * Contrat precedent que celui-ci renouvelle (auto-reference). Permet de retracer une chaine de
     * renouvellements successifs d'un meme assure/produit d'une annee/periode sur l'autre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_precedent_id", foreignKey = @ForeignKey(name = "fk_contract_contrat_precedent"))
    private Contract contratPrecedent;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
