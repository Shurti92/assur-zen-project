package ci.assurzen.repositories;

import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartenaireRepository extends JpaRepository<Partenaire, Long> {

    Optional<Partenaire> findByProfile_Phone(String telephone);

    /**
     * List, pas Optional : rien n'empeche en base plusieurs Partenaire de referencer le meme Profile
     * (aucune contrainte d'unicite sur profile_id - cf. PartenaireService.create()/update()). Un
     * Optional/getSingleResult aurait leve IncorrectResultSizeDataAccessException ("Query did not
     * return a unique result") des qu'un Profile est lie a plus d'un partenaire, plantant le login
     * (POST /auth/login/otp-verify) - bug constate en direct le 2026-08-21, cf. AuthService.buildUserResponse().
     */
    List<Partenaire> findByProfile_Id(Long profileId);

    /** Compte de connexion du partenaire (Partenaire.profile.user) - requete directe, utilisable hors
     * transaction (envoi Web Push asynchrone, 2026-09-26). */
    @Query("select u.id from Partenaire p join p.profile pr join pr.user u where p.id = :partenaireId")
    Optional<Long> findUserIdById(@Param("partenaireId") Long partenaireId);

    /** Rappels periodiques de commissions disponibles (2026-09-23) : partenaires actifs d'une frequence. */
    List<Partenaire> findByActiveTrueAndFrequencePaiementCommissionIn(Collection<FrequencePaiementCommission> frequences);

    Optional<Partenaire> findByCodePartenaire(String codePartenaire);
}
