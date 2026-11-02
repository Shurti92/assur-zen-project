package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(
        name = "t_assur_zen_notificateur_sms",
        indexes = {
                @Index(name = "idx_notificateur_sms_telephone_assure", columnList = "telephone_assure"),
                @Index(name = "idx_notificateur_sms_date_envoi", columnList = "date_envoi"),
                @Index(name = "idx_notificateur_sms_type_message", columnList = "type_message"),
                @Index(name = "idx_notificateur_sms_statut_envoi", columnList = "statut_envoi")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificateurSms implements Serializable {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 30)
    @Column(name = "telephone_assure", nullable = false, length = 30)
    private String telephoneAssure;


    @NotBlank
    @Lob
    @Column(name = "message", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String message;


    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_message", nullable = false, length = 30)
    private TypeMessageSms typeMessage;


    @NotNull
    @Column(name = "date_envoi", nullable = false)
    private LocalDate dateEnvoi;


    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_envoi", nullable = false, length = 30)
    private StatutEnvoiSms statutEnvoi;


    @Lob
    @Column(name = "response_provider", columnDefinition = "MEDIUMTEXT")
    private String responseProvider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", foreignKey = @ForeignKey(name = "fk_notificateur_sms_contract"))
    private Contract contract;
}
