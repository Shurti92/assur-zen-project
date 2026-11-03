package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Question frequente affichee dans l'application mobile, geree depuis le back-office (2026-11-03). */
@Entity
@Table(name = "t_assur_zen_mobile_faq")
@Getter
@Setter
@NoArgsConstructor
public class MobileFaq implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question", nullable = false, length = 300)
    private String question;

    @Column(name = "answer", nullable = false, length = 2000)
    private String answer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
