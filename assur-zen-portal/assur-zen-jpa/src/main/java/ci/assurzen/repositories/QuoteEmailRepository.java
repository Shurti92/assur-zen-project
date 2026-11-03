package ci.assurzen.repositories;

import ci.assurzen.entities.QuoteEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface QuoteEmailRepository extends JpaRepository<QuoteEmail, Long> {

    List<QuoteEmail> findByQuoteIdOrderBySentAtDesc(Long quoteId);

    long countByQuoteIdAndSentAtAfter(Long quoteId, Instant depuis);
}
