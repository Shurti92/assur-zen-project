package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(
        name = "t_assur_zen_ville_partenaire"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VillePartenaire implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 200)
    @Column(name = "nom_ville", nullable = false, length = 200)
    private String nomVille;


    @NotBlank
    @Size(max = 200)
    @Column(name = "regions", nullable = false, length = 200)
    private String regions;


    @Builder.Default
    private boolean active = true;


    @CreationTimestamp
    @Column(updatable = false)
    @JsonIgnore
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    private Instant lastUpdate;
}
