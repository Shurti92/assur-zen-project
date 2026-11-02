package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "t_assur_zen_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_username", columnNames = { "username" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Setter
@ToString(exclude = { "password" })
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String username;
    
    @NonNull
    private String password;


    private String roles;


    @Column(name="first_cnx", length = 1)
    private int firstConnexion;


    @Temporal(TemporalType.DATE)
    @Column(name="date_chang_pwd")
    private Date dateChangPwd = new Date();


    @OneToOne()
    @JoinColumn(name = "type_profile_id", foreignKey = @ForeignKey(name = "fk_ep_users__type_profile_id"))
    private TypeProfile typeProfile;


    @JsonIgnore
    @Column(name = "last_connexion")
    private LocalDate lastConnexion;



    private boolean active;


    @JsonIgnore
    private Instant creation;

    @JsonIgnore
    private Instant lastUpdate;

    /** Compteur d'echecs consecutifs de connexion (mot de passe incorrect uniquement, pas l'OTP). */
    @JsonIgnore
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Non-null tant que le compte est verrouille (cf. AuthService.enforceNotLocked). */
    @JsonIgnore
    @Column(name = "locked_until")
    private Instant lockedUntil;
}
