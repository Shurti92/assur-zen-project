package ci.assurzen.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(
        name = "t_assur_zen_insured"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insured implements Serializable {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 100)
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;


    @NotBlank
    @Size(max = 200)
    @Column(name = "prenoms", nullable = false, length = 200)
    private String prenoms;


    @Column(name = "client_phone", length = 30)
    private String clientPhone;


    /** Second numero de telephone, optionnel - certains assures en ont deux. Recoit les memes SMS de
     * suivi de contrat (avis d'echeance, fin de contrat, relance echeance paiement) que clientPhone. */
    @Column(name = "client_phone_2", length = 30)
    private String clientPhone2;


    @Column(name = "client_city", length = 120)
    private String clientCity;

    @Email
    @Size(max = 150)
    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_client", length = 20)
    private TypeClient typeClient;
}
