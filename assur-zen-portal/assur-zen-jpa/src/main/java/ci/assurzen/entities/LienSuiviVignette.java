package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Lien de suivi public d'une DemandeVignetteMoto (contrat "Suivi public securise par token",
 * 2026-09-17) - l'assure consulte l'evolution de sa demande sans authentification classique via un
 * token opaque transmis par SMS.
 *
 * tokenHash est un SHA-256 DETERMINISTE du token brut, pas un BCrypt comme OtpCode.codeHash : le
 * token brut a deja 256 bits d'entropie (LienSuiviVignetteService genere 32 octets via SecureRandom),
 * donc pas besoin d'un hash lent/sale pour resister au brute-force (contrairement a un OTP a 6
 * chiffres). Un hash deterministe permet en plus une recherche indexee exacte
 * (WHERE token_hash = ?) - impossible avec BCrypt, qui sale differemment a chaque encode() et
 * imposerait de comparer le token candidat a CHAQUE ligne de la table. Le token brut lui-meme n'est
 * jamais persiste : seul son hash l'est, et il n'est renvoye qu'une fois (a la generation/
 * regeneration), jamais relu ensuite.
 */
@Entity
@Table(
        name = "t_assur_zen_lien_suivi_vignette",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lien_suivi_vignette_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_lien_suivi_vignette_demande", columnList = "demande_vignette_moto_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LienSuiviVignette implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_vignette_moto_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lien_suivi_vignette_demande"))
    private DemandeVignetteMoto demandeVignetteMoto;

    @NotNull
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @NotNull
    @Column(name = "date_expiration", nullable = false)
    private Instant dateExpiration;

    @Column(name = "date_derniere_utilisation")
    private Instant dateDerniereUtilisation;

    @Builder.Default
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "date_revocation")
    private Instant dateRevocation;

    @Column(name = "motif_revocation", length = 255)
    private String motifRevocation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
