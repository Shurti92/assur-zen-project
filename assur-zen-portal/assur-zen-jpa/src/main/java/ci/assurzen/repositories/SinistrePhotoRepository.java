package ci.assurzen.repositories;

import ci.assurzen.entities.SinistrePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SinistrePhotoRepository extends JpaRepository<SinistrePhoto, Long> {

    List<SinistrePhoto> findBySinistreIdOrderByCreatedAtAsc(Long sinistreId);

    long countBySinistreIdAndKind(Long sinistreId, String kind);

    Optional<SinistrePhoto> findByIdAndSinistreId(Long id, Long sinistreId);
}
