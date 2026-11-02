package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Demande d'etablissement de vignette moto (nouvelle ou renouvellement). Assure et moto sont des
 * champs libres portes directement par la demande (pas de FK vers Insured/Vehicule) - un demandeur
 * de vignette n'est pas necessairement un client assure existant, et une vignette peut concerner une
 * moto non immatriculee dans le module Flotte (decision produit du 2026-09-11).
 *
 * Le tarif est fige a la creation (montantVignette/fraisOperation/fraisLivraison/fraisAccessoires/
 * montantTotal) - cf. DemandeVignetteMotoService.creer(). tarifVignetteMoto/fraisLivraisonZone ne
 * sont conserves que pour la tracabilite (quelle ligne de configuration a servi), jamais relus pour
 * recalculer un montant apres coup.
 */
@Entity
@Table(
        name = "t_assur_zen_demande_vignette_moto",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_demande_vignette_moto_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uk_demande_vignette_moto_reference", columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_demande_vignette_moto_statut", columnList = "statut")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeVignetteMoto extends AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Builder.Default
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    private String uuid = UUID.randomUUID().toString();

    @NotBlank
    @Size(max = 20)
    @Column(name = "reference", nullable = false, unique = true, length = 20)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_demande", nullable = false, length = 20)
    private TypeDemandeVignette typeDemande;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut", nullable = false, length = 20)
    private StatutDemandeVignetteMoto statut = StatutDemandeVignetteMoto.BROUILLON;

    // ---- Assure (champs libres, cf. javadoc) ----

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom_assure", nullable = false, length = 100)
    private String nomAssure;

    @NotBlank
    @Size(max = 200)
    @Column(name = "prenoms_assure", nullable = false, length = 200)
    private String prenomsAssure;

    /** Nullable (migration - les demandes existantes n'en ont pas) mais obligatoire cote requete
     * pour toute nouvelle demande (contrat "Suivi d'evolution des demandes de vignettes",
     * 2026-09-17) - seul moyen d'envoyer le SMS de suivi de statut a l'assure. */
    @Column(name = "telephone_assure", length = 20)
    private String telephoneAssure;

    /** Optionnel, purement informatif (contact alternatif) - jamais utilise pour un envoi de SMS,
     * contrairement a telephoneAssure (decision produit confirmee le 2026-09-19). */
    @Column(name = "telephone_assure_secondaire", length = 20)
    private String telephoneAssureSecondaire;

    @NotBlank
    @Size(max = 255)
    @Column(name = "adresse_livraison", nullable = false, length = 255)
    private String adresseLivraison;

    @NotBlank
    @Size(max = 120)
    @Column(name = "ville_livraison", nullable = false, length = 120)
    private String villeLivraison;

    // ---- Moto (champs libres, cf. javadoc) ----

    @NotBlank
    @Size(max = 60)
    @Column(name = "marque_moto", nullable = false, length = 60)
    private String marqueMoto;

    @NotBlank
    @Size(max = 60)
    @Column(name = "modele_moto", nullable = false, length = 60)
    private String modeleMoto;

    @NotBlank
    @Size(max = 50)
    @Column(name = "numero_chassis_moto", nullable = false, length = 50)
    private String numeroChassisMoto;

    @NotNull
    @Column(name = "cylindree_moto", nullable = false)
    private Integer cylindreeMoto;

    @NotNull
    @Column(name = "date_premiere_mise_circulation", nullable = false)
    private LocalDate datePremiereMiseCirculation;

    // ---- Tarification figee ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarif_vignette_moto_id", foreignKey = @ForeignKey(name = "fk_demande_vignette_moto_tarif"))
    private TarifVignetteMoto tarifVignetteMoto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frais_livraison_zone_id", foreignKey = @ForeignKey(name = "fk_demande_vignette_moto_zone"))
    private FraisLivraisonZone fraisLivraisonZone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_age_appliquee", nullable = false, length = 20)
    private CategorieAgeMoto categorieAgeAppliquee;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_cylindree_appliquee", nullable = false, length = 20)
    private CategorieCylindree categorieCylindreeAppliquee;

    @NotNull
    @Column(name = "age_moto_calcule", nullable = false)
    private Integer ageMotoCalcule;

    @NotNull
    @Column(name = "montant_vignette", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantVignette;

    @NotNull
    @Column(name = "frais_operation", nullable = false, precision = 12, scale = 2)
    private BigDecimal fraisOperation;

    @NotNull
    @Column(name = "frais_livraison", nullable = false, precision = 12, scale = 2)
    private BigDecimal fraisLivraison;

    @NotNull
    @Column(name = "frais_accessoires", nullable = false, precision = 12, scale = 2)
    private BigDecimal fraisAccessoires;

    @NotNull
    @Column(name = "montant_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantTotal;

    /** Montant deja deduit de montantTotal quand un apporteur est present (contrat "Commission
     * apporteur variable par zone", 2026-09-18) - null si aucun apporteur. Egal au montant credite a
     * l'apporteur (VignetteCommission.montant) - jamais recalcule independamment. */
    @Column(name = "reduction_apporteur", precision = 12, scale = 2)
    private BigDecimal reductionApporteur;

    @Size(max = 500)
    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    // ---- Apporteur (contrat "commission apporteur vignette", 2026-09-15) ----

    /** Exactement un des deux (partenaireApporteur/commercialApporteur) peut etre renseigne, jamais
     * les deux - valide a la creation par DemandeVignetteMotoService, jamais au niveau base (les deux
     * colonnes restent nullable : une demande sans apporteur - vente directe/walk-in - est legitime).
     * Declenche la creation d'une VignetteCommission au moment de la creation de la demande. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_apporteur_id", foreignKey = @ForeignKey(name = "fk_demande_vignette_moto_partenaire_apporteur"))
    private Partenaire partenaireApporteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_apporteur_id", foreignKey = @ForeignKey(name = "fk_demande_vignette_moto_commercial_apporteur"))
    private Commercial commercialApporteur;
}
