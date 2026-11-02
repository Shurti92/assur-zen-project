package ci.assurzen.repositories;

import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.VignetteCommission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VignetteCommissionRepository extends JpaRepository<VignetteCommission, Long> {

    Optional<VignetteCommission> findByDemandeVignetteMotoId(Long demandeVignetteMotoId);

    List<VignetteCommission> findByPartenaireIdAndStatut(Long partenaireId, CommissionStatus statut);

    List<VignetteCommission> findByCommercialIdAndStatut(Long commercialId, CommissionStatus statut);

    List<VignetteCommission> findByStatutOrderByCreatedAtDesc(CommissionStatus statut);

    List<VignetteCommission> findAllByOrderByCreatedAtDesc();

    Page<VignetteCommission> findByPartenaireId(Long partenaireId, Pageable pageable);

    Page<VignetteCommission> findByCommercialId(Long commercialId, Pageable pageable);
}
