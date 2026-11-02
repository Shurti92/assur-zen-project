package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Fiche metier d'un commercial (contrat "Module Commercial", 2026-09-07). Le compte de connexion et
 * les informations personnelles (nom/prenom/email/telephone/localite) ne sont PAS dupliques ici :
 * Commercial pointe vers un Profile existant (meme relation Profile -&gt; User que Partenaire), cree via
 * UserProfileService.create() avec roles="COMMERCIAL" - reutilisation du systeme d'authentification et
 * du mecanisme SMS de bienvenue existants, aucun nouveau systeme de compte.
 *
 * Le lien "quels contrats appartiennent a ce commercial" n'est PAS une nouvelle relation vers Contract :
 * Contract.salesRep (User, deja NOT NULL et cable dans 10+ services) est reutilise tel quel via
 * profile.getUser().getId() - cf. CommercialProductionService.
 */
@Entity
@Table(
        name = "t_assur_zen_commercial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_profile", columnNames = "profile_id"),
                @UniqueConstraint(name = "uk_commercial_employee_code", columnNames = "employee_code")
        },
        indexes = {
                @Index(name = "idx_commercial_statut", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commercial implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_commercial_profile"))
    private Profile profile;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @NotNull
    @Builder.Default
    @Column(name = "transport_benefit_enabled", nullable = false)
    private boolean transportBenefitEnabled = false;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Builder.Default
    @Column(name = "transport_benefit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal transportBenefitAmount = BigDecimal.ZERO;

    @NotNull
    @Builder.Default
    @Column(name = "phone_allowance_enabled", nullable = false)
    private boolean phoneAllowanceEnabled = false;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Builder.Default
    @Column(name = "phone_allowance_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal phoneAllowanceAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutCommercial statut = StatutCommercial.ACTIVE;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
