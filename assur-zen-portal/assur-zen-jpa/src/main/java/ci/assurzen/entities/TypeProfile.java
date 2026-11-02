package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
    name = "t_assur_zen_type_profile",
    uniqueConstraints = {
            @UniqueConstraint(name = "uk_type_profile_libelle", columnNames = "libelle")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "TypeProfile", description = "Modèle Type Profile")
public class TypeProfile implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "libelle", nullable = false, unique = true)
    private String libelle;


    @Builder.Default
    private boolean active = true;


    /**
     * true uniquement sur le TypeProfile "REPRESENTANT" : declenche la creation automatique d'un
     * compte de connexion (username=telephone, mot de passe par defaut) en side-effect de
     * POST/PUT /partenaires - voir PartenaireService.tenterCreationCompteAutomatique().
     */
    @Builder.Default
    @Column(name = "declenche_creation_compte", nullable = false)
    private boolean declencheCreationCompte = false;


    @CreationTimestamp
    @Column(updatable = false)
    @JsonIgnore
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    private Instant lastUpdate;
}
