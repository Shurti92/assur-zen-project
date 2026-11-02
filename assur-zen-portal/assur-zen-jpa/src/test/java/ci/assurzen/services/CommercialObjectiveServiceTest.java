package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialObjectiveRequest;
import ci.assurzen.dto.CommercialObjectiveResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.repositories.CommercialObjectiveRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre l'objectif GLOBAL (annuel) : unicite (commercial, exercice, type) (§17), delegation du
 * controle d'acces/administrateur a CommercialService (pas de duplication de la logique
 * proprietaire/admin), et le perimetre strict d'un objectif a son commercial (getScoped).
 */
class CommercialObjectiveServiceTest {

    private record Fixture(
            CommercialObjectiveService service,
            CommercialObjectiveRepository repository,
            ExerciceComptableRepository exerciceComptableRepository,
            CommercialService commercialService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder
    ) {
    }

    private Fixture newFixture() {
        CommercialObjectiveRepository repository = mock(CommercialObjectiveRepository.class);
        ExerciceComptableRepository exerciceComptableRepository = mock(ExerciceComptableRepository.class);
        CommercialService commercialService = mock(CommercialService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            CommercialObjective o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(idSeq.getAndIncrement());
            }
            return o;
        });

        CommercialObjectiveService service = new CommercialObjectiveService(repository, exerciceComptableRepository,
                commercialService, authService, functionalAuditRecorder);

        return new Fixture(service, repository, exerciceComptableRepository, commercialService, authService, functionalAuditRecorder);
    }

    private Commercial commercial(long id) {
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).build();
    }

    private ExerciceComptable exercice(long id, String libelle) {
        return ExerciceComptable.builder().id(id).libelle(libelle)
                .dateDebut(java.time.LocalDate.of(2026, 1, 1)).dateFin(java.time.LocalDate.of(2026, 12, 31)).build();
    }

    private CommercialObjectiveRequest request(long exerciceId, BigDecimal montant) {
        return new CommercialObjectiveRequest(exerciceId, CommercialObjectiveType.PREMIUM_AMOUNT, montant, null);
    }

    // ---------------------------------------------------------------- create()

    @Test
    void create_succes_persisteLobjectifRattacheAuCommercialEtALexercice() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        ExerciceComptable ex = exercice(10, "Exercice 2026");
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.exerciceComptableRepository().findById(10L)).thenReturn(Optional.of(ex));
        when(fx.repository().findByCommercialIdAndExerciceComptableIdAndObjectiveType(1L, 10L, CommercialObjectiveType.PREMIUM_AMOUNT))
                .thenReturn(Optional.empty());

        CommercialObjectiveResponse response = fx.service().create(1L, request(10L, BigDecimal.valueOf(60_000_000)));

        assertEquals(1L, response.commercialId());
        assertEquals(10L, response.exerciceComptableId());
        assertEquals("Exercice 2026", response.exerciceLibelle());
        assertEquals(0, BigDecimal.valueOf(60_000_000).compareTo(response.targetAmount()));
        verify(fx.commercialService()).requireAdmin();
    }

    @Test
    void create_objectifDejaExistantPourCeCommercialCetExerciceCeType_estRejeteAvec409() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        ExerciceComptable ex = exercice(10, "Exercice 2026");
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.exerciceComptableRepository().findById(10L)).thenReturn(Optional.of(ex));
        CommercialObjective existant = CommercialObjective.builder().id(5L).commercial(c).exerciceComptable(ex)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.TEN).build();
        when(fx.repository().findByCommercialIdAndExerciceComptableIdAndObjectiveType(1L, 10L, CommercialObjectiveType.PREMIUM_AMOUNT))
                .thenReturn(Optional.of(existant));

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(1L, request(10L, BigDecimal.valueOf(60_000_000))));
        assertEquals(HttpStatus.CONFLICT, ex2.getStatusCode());
        verify(fx.repository(), never()).save(any());
    }

    @Test
    void create_exerciceComptableIntrouvable_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.commercialService().getCommercial(1L)).thenReturn(commercial(1));
        when(fx.exerciceComptableRepository().findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(1L, request(999L, BigDecimal.valueOf(60_000_000))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void create_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(1L, request(10L, BigDecimal.valueOf(1000))));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.exerciceComptableRepository(), never()).findById(any());
    }

    // ---------------------------------------------------------------- update()

    @Test
    void update_succes_traceLauditFonctionnelAvecAvantApres() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        ExerciceComptable ex = exercice(10, "Exercice 2026");
        CommercialObjective objectif = CommercialObjective.builder().id(5L).commercial(c).exerciceComptable(ex)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(60_000_000)).build();
        when(fx.repository().findById(5L)).thenReturn(Optional.of(objectif));

        CommercialObjectiveResponse response = fx.service().update(1L, 5L, request(10L, BigDecimal.valueOf(75_000_000)));

        assertEquals(0, BigDecimal.valueOf(75_000_000).compareTo(response.targetAmount()));
        verify(fx.functionalAuditRecorder()).recordUpdate(eq("COMMERCIAL_OBJECTIVE"), eq("CommercialObjective"), eq("5"), any(), any(), any());
    }

    @Test
    void update_objectifIntrouvable_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.repository().findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().update(1L, 5L, request(10L, BigDecimal.valueOf(1000))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void update_objectifAppartenantAUnAutreCommercial_estRejeteAvec404_perimetreStrict() {
        Fixture fx = newFixture();
        Commercial autreCommercial = commercial(2);
        ExerciceComptable ex = exercice(10, "Exercice 2026");
        CommercialObjective objectifDuncAutre = CommercialObjective.builder().id(5L).commercial(autreCommercial)
                .exerciceComptable(ex).objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.TEN).build();
        when(fx.repository().findById(5L)).thenReturn(Optional.of(objectifDuncAutre));

        // Le path demande l'objectif 5 du commercial 1, mais l'objectif appartient au commercial 2.
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> fx.service().update(1L, 5L, request(10L, BigDecimal.valueOf(1000))));
        assertEquals(HttpStatus.NOT_FOUND, ex2.getStatusCode());
    }

    // ---------------------------------------------------------------- list()

    @Test
    void list_delegueLeControleDaccesEtLaVerificationDexistenceACommercialService() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        ExerciceComptable ex2025 = exercice(9, "Exercice 2025");
        ExerciceComptable ex2026 = exercice(10, "Exercice 2026");
        CommercialObjective o1 = CommercialObjective.builder().id(1L).commercial(c).exerciceComptable(ex2025)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(50_000_000)).build();
        CommercialObjective o2 = CommercialObjective.builder().id(2L).commercial(c).exerciceComptable(ex2026)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(60_000_000)).build();
        when(fx.repository().findByCommercialIdOrderByExerciceComptableIdDesc(1L)).thenReturn(List.of(o2, o1));
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);

        List<CommercialObjectiveResponse> objectifs = fx.service().list(1L);

        assertEquals(2, objectifs.size());
        verify(fx.commercialService()).verifierAccesCommercialCible(1L);
        verify(fx.commercialService()).getCommercial(1L);
    }
}
