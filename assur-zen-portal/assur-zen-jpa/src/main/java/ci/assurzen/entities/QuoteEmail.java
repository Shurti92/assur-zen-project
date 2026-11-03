package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Trace d'un envoi de devis par e-mail (qui, quand, a qui, avec ou sans PDF) - 2026-11-03. */
@Entity
@Table(name = "t_assur_zen_quote_email", indexes = @Index(name = "idx_quote_email_quote", columnList = "quote_id, sent_at"))
@Getter
@Setter
@NoArgsConstructor
public class QuoteEmail implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "to_email", nullable = false, length = 150)
    private String toEmail;

    @Column(name = "sent_by", nullable = false, length = 100)
    private String sentBy;

    @Column(name = "with_pdf", nullable = false)
    private boolean withPdf;

    @Column(name = "copy_to", length = 150)
    private String copyTo;
}
