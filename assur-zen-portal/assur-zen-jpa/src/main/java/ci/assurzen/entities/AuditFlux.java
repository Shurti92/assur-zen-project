package ci.assurzen.entities;
import ci.assurzen.enums.Direction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_audit_flux",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_audit_flux_key",
                        columnNames = {"code", "direction"}
                )
        },
        indexes = {
                @Index(name = "idx_audit_code", columnList = "code"),
                @Index(name = "idx_audit_direction", columnList = "direction"),
                @Index(name = "idx_audit_route", columnList = "route"),
                @Index(name = "idx_audit_username", columnList = "username"),
                @Index(name = "idx_audit_status_code", columnList = "status_code"),
                @Index(name = "idx_audit_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditFlux implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "code", updatable = false)
    private String code;


    @CreationTimestamp
    @Column(name = "creation", updatable = false, nullable = false)
    private Instant creation;


    @Column(name = "operation", updatable = false)
    private String operation;


    @Lob
    @Column(name = "flux", updatable = false, nullable = false, columnDefinition = "MEDIUMTEXT")
    private String flux;


    @Lob
    @Column(name = "exception_message", updatable = false, columnDefinition = "MEDIUMTEXT")
    private String exceptionMessage;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;


    @Column(name = "username")
    private String username;


    @Column(name = "numero_contrat")
    private String numeroContrat;


    @Column(name = "route")
    private String route;


    @Column(name = "status_code")
    private Integer statusCode;


    @Column(name = "ip_address", length = 45)
    private String ipAddress;


    @Column(name = "user_id")
    private Long userId;


    @Column(name = "duration_ms")
    private Long durationMs;
}
