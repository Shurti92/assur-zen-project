package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.FraisLivraisonZoneRequest;
import ci.assurzen.dto.FraisLivraisonZoneResponse;
import ci.assurzen.entities.FraisLivraisonZone;
import ci.assurzen.repositories.FraisLivraisonZoneRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre §8 (zones extensibles, code libre pas un enum) et §10/§11 (resolution utilisee par
 * DemandeVignetteMotoService pour figer le montant de livraison). */
class FraisLivraisonZoneServiceTest {

    private record Fixture(FraisLivraisonZoneService service, FraisLivraisonZoneRepository repository, AuthService authService) {
    }

    private Fixture newFixture() {
        FraisLivraisonZoneRepository repository = mock(FraisLivraisonZoneRepository.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            FraisLivraisonZone z = inv.getArgument(0);
            if (z.getId() == null) {
                z.setId(idSeq.getAndIncrement());
            }
            return z;
        });

        return new Fixture(new FraisLivraisonZoneService(repository, authService, functionalAuditRecorder), repository, authService);
    }

    private FraisLivraisonZone zone(long id, String code, BigDecimal montant, boolean actif) {
        return FraisLivraisonZone.builder().id(id).code(code).libelle(code).montant(montant).actif(actif).build();
    }

    // ---------------------------------------------------------------- resoudre()

    @Test
    void resoudre_codeConnuEtActif_renvoieLaZone() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeAndActifTrue("ABIDJAN")).thenReturn(Optional.of(zone(1, "ABIDJAN", BigDecimal.valueOf(1500), true)));

        FraisLivraisonZone resultat = fx.service().resoudre("ABIDJAN");

        assertEquals(0, BigDecimal.valueOf(1500).compareTo(resultat.getMontant()));
    }

    @Test
    void resoudre_codeInconnu_leve404() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeAndActifTrue("INEXISTANTE")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudre("INEXISTANTE"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void resoudre_zoneDesactivee_leve404_findByCodeAndActifTrueNeLaRenvoiePas() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeAndActifTrue("ABIDJAN")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudre("ABIDJAN"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void resoudre_codeVide_leve400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudre(""));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- create()

    @Test
    void create_succes_normaliseLeCodeEnMajuscules() {
        Fixture fx = newFixture();
        when(fx.repository().existsByCode("BOUAKE")).thenReturn(false);

        FraisLivraisonZoneResponse response = fx.service().create(new FraisLivraisonZoneRequest("bouake", "Bouake", BigDecimal.valueOf(2000)));

        assertEquals("BOUAKE", response.code());
    }

    @Test
    void create_codeDejaExistant_leve409_neSauvegardePas() {
        Fixture fx = newFixture();
        when(fx.repository().existsByCode("ABIDJAN")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(new FraisLivraisonZoneRequest("ABIDJAN", "Abidjan", BigDecimal.valueOf(1500))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.repository(), never()).save(any());
    }

    @Test
    void create_sansDroitsAdministrateur_leve403() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "user", "USER"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(new FraisLivraisonZoneRequest("ABIDJAN", "Abidjan", BigDecimal.valueOf(1500))));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- desactiver()

    @Test
    void desactiver_succes_basculeActifAFaux() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L)).thenReturn(Optional.of(zone(1, "ABIDJAN", BigDecimal.valueOf(1500), true)));

        FraisLivraisonZoneResponse response = fx.service().desactiver(1L);

        assertFalse(response.actif());
    }

    @Test
    void desactiver_zoneIntrouvable_leve404() {
        Fixture fx = newFixture();
        when(fx.repository().findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().desactiver(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
