package ci.assurzen.repositories;

import ci.assurzen.entities.PartenaireDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartenaireDocumentRepository extends JpaRepository<PartenaireDocument, Long> {

    List<PartenaireDocument> findByPartenaireIdOrderByCreatedAtAsc(Long partenaireId);

    Optional<PartenaireDocument> findByIdAndPartenaireId(Long id, Long partenaireId);

    /** Documents a verifier (EN_ATTENTE, ou sans statut : anterieurs au 2026-10-04) - repere du back-office. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(d) FROM PartenaireDocument d WHERE d.partenaire.id = :partenaireId"
            + " AND (d.statutValidation IS NULL OR d.statutValidation = ci.assurzen.entities.StatutValidationDocument.EN_ATTENTE)")
    long countEnAttente(@org.springframework.data.repository.query.Param("partenaireId") Long partenaireId);

    /** Rappels d'expiration (2026-10-04). */
    List<PartenaireDocument> findByDateExpirationIn(java.util.Collection<java.time.LocalDate> dates);
}
