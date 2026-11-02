package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.TarifVignetteMotoRequest;
import ci.assurzen.dto.TarifVignetteMotoResponse;
import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.TarifVignetteMoto;
import ci.assurzen.repositories.TarifVignetteMotoRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre §9 (resolution du tarif applicable, jamais relue pour une demande deja creee - le figeage
 * lui-meme est teste dans DemandeVignetteMotoServiceTest), §13 (garde-fou anti-chevauchement) et §20
 * (audit avant/apres sur update).
 */
class TarifVignetteMotoServiceTest {

    private record Fixture(TarifVignetteMotoService service, TarifVignetteMotoRepository repository,
                            AuthService authService, FunctionalAuditRecorder functionalAuditRecorder) {
    }

    private Fixture newFixture() {
        TarifVignetteMotoRepository repository = mock(TarifVignetteMotoRepository.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            TarifVignetteMoto t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(idSeq.getAndIncrement());
            }
            return t;
        });

        return new Fixture(new TarifVignetteMotoService(repository, authService, functionalAuditRecorder),
                repository, authService, functionalAuditRecorder);
    }

    private TarifVignetteMoto tarif(long id, CategorieAgeMoto age, CategorieCylindree cylindree,
                                     LocalDate debut, LocalDate fin, boolean actif) {
        return TarifVignetteMoto.builder().id(id).categorieAge(age).categorieCylindree(cylindree)
                .montantVignette(BigDecimal.valueOf(3750)).fraisOperation(BigDecimal.valueOf(1500))
                .dateDebutValidite(debut).dateFinValidite(fin).actif(actif).build();
    }

    private TarifVignetteMotoRequest request(CategorieAgeMoto age, CategorieCylindree cylindree, LocalDate debut, LocalDate fin) {
        return new TarifVignetteMotoRequest(age, cylindree, BigDecimal.valueOf(3750), BigDecimal.valueOf(1500), debut, fin);
    }

    // ---------------------------------------------------------------- resoudre()

    @Test
    void resoudre_unSeulTarifApplicable_leRenvoie() {
        Fixture fx = newFixture();
        TarifVignetteMoto t = tarif(1, CategorieAgeMoto.AGE_5_10_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findTarifsApplicables(CategorieAgeMoto.AGE_5_10_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(t));

        TarifVignetteMoto resultat = fx.service().resoudre(CategorieAgeMoto.AGE_5_10_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 6, 1));

        assertEquals(1L, resultat.getId());
    }

    @Test
    void resoudre_aucunTarifApplicable_leve404_tarifInexistantOuDesactiveOuHorsPeriode() {
        Fixture fx = newFixture();
        when(fx.repository().findTarifsApplicables(any(), any(), any())).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().resoudre(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.now()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void resoudre_plusieursTarifsApplicablesEnMemeTemps_configurationIncoherente_leve409() {
        Fixture fx = newFixture();
        TarifVignetteMoto t1 = tarif(1, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null, true);
        TarifVignetteMoto t2 = tarif(2, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findTarifsApplicables(any(), any(), any())).thenReturn(List.of(t1, t2));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().resoudre(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.now()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- create()

    @Test
    void create_aucunChevauchement_persisteLeTarif() {
        Fixture fx = newFixture();
        when(fx.repository().findByCategorieAgeAndCategorieCylindreeAndActifTrue(any(), any())).thenReturn(List.of());

        TarifVignetteMotoResponse response = fx.service().create(
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null));

        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, response.categorieAge());
    }

    @Test
    void create_chevaucheUnTarifActifExistant_leve409_neSauvegardePas() {
        Fixture fx = newFixture();
        TarifVignetteMoto existant = tarif(1, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findByCategorieAgeAndCategorieCylindreeAndActifTrue(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3))
                .thenReturn(List.of(existant));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 6, 1), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.repository(), never()).save(any());
    }

    @Test
    void create_periodeNeChevauchePasCarTarifExistantEstDejaFerme_estAccepte() {
        Fixture fx = newFixture();
        TarifVignetteMoto ferme = tarif(1, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), true);
        when(fx.repository().findByCategorieAgeAndCategorieCylindreeAndActifTrue(any(), any())).thenReturn(List.of(ferme));

        TarifVignetteMotoResponse response = fx.service().create(
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null));

        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, response.categorieAge());
    }

    @Test
    void create_dateFinAvantDateDebut_leve400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_sansDroitsAdministrateur_leve403() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "user", "USER"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- update()

    @Test
    void update_succes_traceLauditFonctionnelAvecAvantApres() {
        Fixture fx = newFixture();
        TarifVignetteMoto existant = tarif(5, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findById(5L)).thenReturn(Optional.of(existant));
        when(fx.repository().findByCategorieAgeAndCategorieCylindreeAndActifTrue(any(), any())).thenReturn(List.of(existant));

        fx.service().update(5L, request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null));

        verify(fx.functionalAuditRecorder()).recordUpdate(eq("VIGNETTES_MOTO"), eq("TarifVignetteMoto"), eq("5"), any(), any(), any());
    }

    @Test
    void update_neSeChevauchePasAvecLuiMeme_periodeInchangee() {
        Fixture fx = newFixture();
        TarifVignetteMoto existant = tarif(5, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findById(5L)).thenReturn(Optional.of(existant));
        when(fx.repository().findByCategorieAgeAndCategorieCylindreeAndActifTrue(any(), any())).thenReturn(List.of(existant));

        TarifVignetteMotoResponse response = fx.service().update(5L,
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null));

        assertEquals(5L, response.id());
    }

    @Test
    void update_tarifIntrouvable_leve404() {
        Fixture fx = newFixture();
        when(fx.repository().findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(5L,
                request(CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3, LocalDate.of(2026, 1, 1), null)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- desactiver()

    @Test
    void desactiver_succes_basculeActifAFaux() {
        Fixture fx = newFixture();
        TarifVignetteMoto existant = tarif(5, CategorieAgeMoto.AGE_1_4_ANS, CategorieCylindree.MOINS_125_CM3,
                LocalDate.of(2026, 1, 1), null, true);
        when(fx.repository().findById(5L)).thenReturn(Optional.of(existant));

        TarifVignetteMotoResponse response = fx.service().desactiver(5L);

        assertFalse(response.actif());
    }
}
