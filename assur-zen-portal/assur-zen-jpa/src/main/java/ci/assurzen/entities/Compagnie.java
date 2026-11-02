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

@Entity
@Table(
        name = "t_assur_zen_compagnie",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_compagnie_code", columnNames = "code_compagnie")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compagnie implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank
    @Size(max = 20)
    @Column(name = "code_compagnie", nullable = false, unique = true, length = 20)
    private String codeCompagnie;


    @NotBlank
    @Size(max = 200)
    @Column(name = "nom", nullable = false, length = 200)
    private String nom;


    /** Nullable : champ ajoute apres coup, les compagnies deja en base n'ont pas de valeur retroactive. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10)
    private TypeCompagnie type;


    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonIgnore
    private Instant creation;


    @UpdateTimestamp
    @JsonIgnore
    @Column(nullable = false)
    private Instant lastUpdate;
}
