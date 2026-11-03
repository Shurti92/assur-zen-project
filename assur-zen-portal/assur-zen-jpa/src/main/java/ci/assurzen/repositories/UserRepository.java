package ci.assurzen.repositories;

import ci.assurzen.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Comptes mobile (role CLIENT), filtrables par numero ou nom (administration, 2026-11-03). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT u FROM User u
            WHERE u.roles = 'CLIENT'
              AND (:q IS NULL OR u.username LIKE CONCAT('%', :q, '%')
                   OR EXISTS (SELECT 1 FROM Profile p WHERE p.user.id = u.id
                              AND LOWER(CONCAT(p.firstname, ' ', p.lastname)) LIKE LOWER(CONCAT('%', :q, '%'))))
              AND (:actif IS NULL OR u.active = :actif)
            """)
    org.springframework.data.domain.Page<User> rechercherComptesMobile(
            @org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("actif") Boolean actif,
            org.springframework.data.domain.Pageable pageable);


    Optional<User> findByUsername(String username);
}
