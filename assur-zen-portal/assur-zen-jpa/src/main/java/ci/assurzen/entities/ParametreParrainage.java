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
        name = "t_assur_zen_parametre_parrainage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_parametre_parrainage_cle", columnNames = "cle")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametreParrainage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "cle", nullable = false, unique = true, length = 50)
    private String cle;

    @NotBlank
    @Size(max = 200)
    @Column(name = "valeur", nullable = false, length = 200)
    private String valeur;

    @Size(max = 300)
    @Column(name = "description", length = 300)
    private String description;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonIgnore
    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;
}
