package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_otp_codes",
        indexes = {
                @Index(name = "idx_otp_code_phone", columnList = "phone"),
                @Index(name = "idx_otp_code_consumed", columnList = "consumed")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @NotBlank
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Code demande via /auth/mobile/otp/request (REGISTER / RESET_PIN) : seul ce type compte dans le quota horaire
     * (2026-11-02). Nullable : les anciennes lignes et les codes de connexion (2FA) n'y comptent pas. */
    @Column(name = "mobile_request")
    private Boolean mobileRequest;

    /** Objet du code (2026-11-02) : REGISTER ou RESET_PIN pour les demandes mobile ; null = code de connexion / portail.
     * Un code n'est accepte que pour son objet (un code de login ne reinitialise pas un PIN, et inversement). */
    @Column(name = "purpose", length = 20)
    private String purpose;
}
