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

    @Column(name = "total", nullable = false)
    private Long total;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
