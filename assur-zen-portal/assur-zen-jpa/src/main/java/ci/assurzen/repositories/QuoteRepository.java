package ci.assurzen.repositories;

import ci.assurzen.entities.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Quote> findByReferenceAndUserId(String reference, Long userId);

    Optional<Quote> findByReference(String reference);

    List<Quote> findAllByOrderByCreatedAtDesc();

    List<Quote> findByCreatedByUserIdOrderByCreatedAtDesc(Long createdByUserId);

    @Modifying
    @Query("update Quote q set q.status = 'EXPIRE' where q.status = 'CALCULE' and q.validUntil < :today")
    int expireJusqua(LocalDate today);
}
