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
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_profile_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_profile_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_profile_code", columnList = "code"),
                @Index(name = "idx_profile_phone", columnList = "phone"),
                @Index(name = "idx_profile_user", columnList = "user_id"),
                @Index(name = "idx_profile_ville", columnList = "ville_partenaire_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //==================================================
    // Identifiant technique
    //==================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //==================================================
    // Identifiant métier
    //==================================================

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    //==================================================
    // Informations personnelles
    //==================================================

    @NotBlank
    @Size(max = 150)
    @Column(name = "firstname", nullable = false, length = 150)
    private String firstname;


    @NotBlank
    @Size(max = 150)
    @Column(name = "lastname", nullable = false, length = 150)
    private String lastname;


    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;


    @Size(max = 20)
    @Column(name = "role", length = 20)
    private String role;

    @Size(max = 150)
    @Column(name = "email", length = 150, unique = true)
    private String email;


    @Size(max = 150)
    @Column(name = "sector", length = 150)
    private String sector;

    /** Langue preferee de l'utilisateur (code de t_assur_zen_communication_language, ex "FR") ; survit a un changement de poste, cf. docs/api-contracts/i18n.md. */
    @Size(max = 10)
    @Column(name = "langue_code", length = 10)
    private String langueCode;


    @Size(max = 255)
    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;


    @Size(max = 1000)
    @Column(name = "bio", length = 1000)
    private String bio;

    //==================================================
    // Reseaux sociaux
    //==================================================

    @Size(max = 255)
    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Size(max = 255)
    @Column(name = "twitter_url", length = 255)
    private String twitterUrl;

    @Size(max = 255)
    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Size(max = 255)
    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    //==================================================
    // Adresse
    //==================================================

    @Size(max = 100)
    @Column(name = "country", length = 100)
    private String country;

    @Size(max = 100)
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100)
    @Column(name = "state", length = 100)
    private String state;

    @Size(max = 20)
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Size(max = 50)
    @Column(name = "tax_id", length = 50)
    private String taxId;

    //==================================================
    // Securite
    //==================================================

    @Builder.Default
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = true;

    //==================================================
    // Relations
    //==================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_profiles_user")
    )
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ville_partenaire_id",
            foreignKey = @ForeignKey(name = "fk_profiles_ville_partenaire")
    )
    private VillePartenaire villePartenaire;

    //==================================================
    // Statut
    //==================================================

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;


    @Builder.Default
    @Column(nullable = false)
    private boolean expiration = false;

    //==================================================
    // Optimistic Lock
    //==================================================

    @Version
    private Long version;

    //==================================================
    // Audit
    //==================================================

    @CreationTimestamp
    @JsonIgnore
    @Column(nullable = false, updatable = false)
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;

}
