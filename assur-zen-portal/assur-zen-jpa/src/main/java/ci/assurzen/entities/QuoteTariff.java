package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Ligne de la grille tarifaire des devis en ligne (2026-11-03) : un montant par produit, formule, categorie de vehicule
 * et duree. Montants en F CFA. Le total doit etre egal a la somme des composantes (verifie a l'ecriture).
 */
@Entity
@Table(name = "t_assur_zen_quote_tariff",
        uniqueConstraints = @UniqueConstraint(name = "uk_quote_tariff", columnNames = {"product_key", "formula_id", "vehicle_category", "duration_months"}))
@Getter
@Setter
@NoArgsConstructor
public class QuoteTariff implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cle du produit du catalogue mobile : moto. */
    @Column(name = "product_key", nullable = false, length = 40)
    private String productKey;

    @Column(name = "formula_id", nullable = false, length = 40)
    private String formulaId;

    /** MOTO ou TRICYCLE. */
    @Column(name = "vehicle_category", nullable = false, length = 20)
    private String vehicleCategory;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "net_premium", nullable = false)
    private Long netPremium;

    @Column(name = "taxes", nullable = false)
    private Long taxes;

    @Column(name = "accessories", nullable = false)
    private Long accessories;

    @Column(name = "fga", nullable = false)
    private Long fga;

    /** Frais CEDEAO (carte brune) et attestation : fixes par tarif, 0 si non applicable. */
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "cedeao", nullable = false)
    private Long cedeao = 0L;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "attestation", nullable = false)
    private Long attestation = 0L;

    @Column(name = "total", nullable = false)
    private Long total;

    /** Criteres d'application (VTC) : puissance fiscale minimale, nombre de places min et max. Null = sans condition. */
    /** Taux de taxe en % de la prime nette apres remise (VTC : 14,5). Null = taxes fixes (montant du tarif), cas de la moto. */
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private java.math.BigDecimal taxRate;

    @Column(name = "min_fiscal_power")
    private Integer minFiscalPower;

    @Column(name = "min_seats")
    private Integer minSeats;

    @Column(name = "max_seats")
    private Integer maxSeats;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
