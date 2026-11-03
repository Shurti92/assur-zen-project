package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Devis en ligne enregistre (lot 2, 2026-11-03). Le prix est FIGE a la creation : modifier la grille ensuite n'affecte
 * jamais un devis existant. Statuts : CALCULE, ACCEPTE, EXPIRE (CONVERTI arrive avec la souscription, lot 3).
 */
@Entity
@Table(name = "t_assur_zen_quote",
        indexes = {@Index(name = "idx_quote_user", columnList = "user_id, created_at"), @Index(name = "idx_quote_status", columnList = "status, valid_until")})
@Getter
@Setter
@NoArgsConstructor
public class Quote implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DEV-AAAA-NNNNNN. */
    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    /** Compte mobile proprietaire (null pour un devis cree au back-office, lot 3). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_key", nullable = false, length = 40)
    private String productKey;

    @Column(name = "formula_id", nullable = false, length = 40)
    private String formulaId;

    @Column(name = "formula_name", nullable = false, length = 100)
    private String formulaName;

    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType;

    @Column(name = "cylinder_cc")
    private Integer cylinderCc;

    @Column(length = 60)
    private String brand;

    @Column(length = 60)
    private String model;

    @Column(name = "registration_number", length = 20)
    private String registrationNumber;

    @Column(name = "chassis_number", length = 30)
    private String chassisNumber;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "net_premium", nullable = false)
    private Long netPremium;

    @Column(nullable = false)
    private Long taxes;

    @Column(nullable = false)
    private Long accessories;

    @Column(nullable = false)
    private Long fga;

    @Column(nullable = false)
    private Long total;

    @Column(nullable = false, length = 20)
    private String status = "CALCULE";

    /** Dernier jour ou le devis peut etre accepte. */
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** MOBILE (client connecte) ou BACKOFFICE (commercial / administrateur). */
    @Column(nullable = false, length = 20)
    private String channel = "MOBILE";

    /** Utilisateur du back-office qui a cree le devis (canal BACKOFFICE). */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "prospect_name", length = 150)
    private String prospectName;

    @Column(name = "prospect_phone", length = 20)
    private String prospectPhone;

    // ---- Assurance automobile VTC (2026-11-03)
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(nullable = false)
    private Long cedeao = 0L;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(nullable = false)
    private Long attestation = 0L;

    /** Remise accordee (back-office) sur la prime nette : pourcentage et montant en F CFA. */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    private java.math.BigDecimal discountPercent;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L;

    @Column(name = "fiscal_power")
    private Integer fiscalPower;

    @Column(length = 20)
    private String energy;

    @Column
    private Integer seats;

    @Column(name = "first_registration_year")
    private Integer firstRegistrationYear;

    @Column(name = "vtc_declared")
    private Boolean vtcDeclared;

    @Column(length = 60)
    private String platform;
}
