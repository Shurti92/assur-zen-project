package ci.assurzen.repositories;

import ci.assurzen.entities.Sinistre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SinistreRepository extends JpaRepository<Sinistre, Long> {

    Optional<Sinistre> findByNumero(String numero);

    List<Sinistre> findByContractIdOrderByCreatedAtDesc(Long contractId);

    List<Sinistre> findByInsuredIdOrderByCreatedAtDesc(Long insuredId);

    /** Sinistres des assures donnes, avec contrat et produit (lecture mobile, 2026-11-02). */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"contract", "contract.product"})
    List<Sinistre> findByInsuredIdInOrderByCreatedAtDesc(java.util.Collection<Long> insuredIds);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"contract", "contract.product"})
    java.util.Optional<Sinistre> findWithContractByIdAndInsuredIdIn(Long id, java.util.Collection<Long> insuredIds);

    long countByContractIdAndStatusNotIn(Long contractId, java.util.Collection<ci.assurzen.entities.SinistreStatus> statuts);

    Optional<Sinistre> findByIdAndContract_SalesRep_Id(Long id, Long salesRepId);
}
