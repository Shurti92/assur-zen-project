package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialMonthlyObjectiveRequest;
import ci.assurzen.dto.CommercialMonthlyObjectiveResponse;
import ci.assurzen.dto.CommercialMonthlyObjectiveRevisionRequest;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialMonthlyObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.repositories.CommercialMonthlyObjectiveRepository;
import ci.assurzen.repositories.MoisComptableRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre l'objectif MENSUEL (§18-20) : unicite (commercial, moisComptable, type) avec message
 * orientant vers la revision plutot qu'une recreation (§19), revision qui modifie la MEME ligne et
 * trace le motif dans l'audit fonctionnel plutot que d'ecrire une table d'historique dediee (§32/§33),
 * et le perimetre strict d'un objectif a son commercial.
 */
class CommercialMonthlyObjectiveServiceTest {

    private record Fixture(
            CommercialMonthlyObjectiveService service,
            CommercialMonthlyObjectiveRepository repository,
            MoisComptableRepository moisComptableRepository,
            CommercialService commercialService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder
    ) {
    }

    private Fixture newFixture() {
        CommercialMonthlyObjectiveRepository repository = mock(CommercialMonthlyObjectiveRepository.class);
        MoisComptableRepository moisComptableRepository = mock(MoisComptableRepository.class);
        CommercialService commercialService = mock(CommercialService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            CommercialMonthlyObjective o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(idSeq.getAndIncrement());
            }
            return o;
        });

        CommercialMonthlyObjectiveService service = new CommercialMonthlyObjectiveService(repository, moisComptableRepository,
                commercialService, authService, functionalAuditRecorder);

        return new Fixture(service, repository, moisComptableRepository, commercialService, authService, functionalAuditRecorder);
    }

    private Commercial commercial(long id) {
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).build();
    }

    private MoisComptable mois(long id, int numero, String libelle) {
        return MoisComptable.builder().id(id).numero(numero).libelle(libelle)
                .dateDebut(LocalDate.of(2026, numero, 1)).dateFin(LocalDate.of(2026, numero, 28)).cloture(false).build();
    }

    private CommercialMonthlyObjectiveRequest request(long moisId, BigDecimal montant) {
        return new CommercialMonthlyObjectiveRequest(moisId, CommercialObjectiveType.PREMIUM_AMOUNT, montant, null);
    }

    // ---------------------------------------------------------------- create()

    @Test
    void create_succes_persisteLobjectifRattacheAuCommercialEtAuMoisComptable() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10, 1, "Janvier 2026");
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.repository().findByCommercialIdAndMoisComptableIdAndObjectiveType(1L, 10L, CommercialObjectiveType.PREMIUM_AMOUNT))
                .thenReturn(Optional.empty());

        CommercialMonthlyObjectiveResponse response = fx.service().create(1L, request(10L, BigDecimal.valueOf(5_000_000)));

        assertEquals(1L, response.commercialId());
        assertEquals(10L, response.moisComptableId());
        assertEquals("Janvier 2026", response.moisLibelle());
        assertEquals(0, BigDecimal.valueOf(5_000_000).compareTo(response.targetAmount()));
    }

    @Test
    void create_objectifDejaExistantPourCeMoisCeType_estRejeteAvec409_orienteVersLaRevision() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10, 1, "Janvier 2026");
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        CommercialMonthlyObjective existant = CommercialMonthlyObjective.builder().id(7L).commercial(c).moisComptable(m)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(5_000_000)).build();
        when(fx.repository().findByCommercialIdAndMoisComptableIdAndObjectiveType(1L, 10L, CommercialObjectiveType.PREMIUM_AMOUNT))
                .thenReturn(Optional.of(existant));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(1L, request(10L, BigDecimal.valueOf(6_000_000))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.repository(), never()).save(any());
    }

    @Test
    void create_moisComptableIntrouvable_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.commercialService().getCommercial(1L)).thenReturn(commercial(1));
        when(fx.moisComptableRepository().findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(1L, request(999L, BigDecimal.valueOf(1000))));
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
        verify(fx.moisComptableRepository(), never()).findById(any());
    }

    // ---------------------------------------------------------------- reviser() (§32/§33)

    @Test
    void reviser_succes_modifieLaMemeLigneEtTraceLeMotifDansLauditFonctionnel() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10, 1, "Janvier 2026");
        CommercialMonthlyObjective objectif = CommercialMonthlyObjective.builder().id(7L).commercial(c).moisComptable(m)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(5_000_000)).build();
        when(fx.repository().findById(7L)).thenReturn(Optional.of(objectif));

        CommercialMonthlyObjectiveResponse response = fx.service().reviser(1L, 7L,
                new CommercialMonthlyObjectiveRevisionRequest(BigDecimal.valueOf(6_500_000), null, "revision a la hausse suite au recrutement"));

        assertEquals(7L, response.id());
        assertEquals(0, BigDecimal.valueOf(6_500_000).compareTo(response.targetAmount()));
        // Meme ligne modifiee, pas une nouvelle (§32/§33) : un seul appel a save(), sur l'objectif existant.
        verify(fx.repository(), times(1)).save(objectif);
        verify(fx.functionalAuditRecorder()).recordUpdate(eq("COMMERCIAL_OBJECTIVE"), eq("CommercialMonthlyObjective"), eq("7"),
                any(), any(), eq("revision a la hausse suite au recrutement"));
    }

    @Test
    void reviser_objectifIntrouvable_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.repository().findById(7L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().reviser(1L, 7L, new CommercialMonthlyObjectiveRevisionRequest(BigDecimal.valueOf(1000), null, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void reviser_objectifAppartenantAUnAutreCommercial_estRejeteAvec404_perimetreStrict() {
        Fixture fx = newFixture();
        Commercial autreCommercial = commercial(2);
        MoisComptable m = mois(10, 1, "Janvier 2026");
        CommercialMonthlyObjective objectifDunAutre = CommercialMonthlyObjective.builder().id(7L).commercial(autreCommercial)
                .moisComptable(m).objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.TEN).build();
        when(fx.repository().findById(7L)).thenReturn(Optional.of(objectifDunAutre));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().reviser(1L, 7L, new CommercialMonthlyObjectiveRevisionRequest(BigDecimal.valueOf(1000), null, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- list()

    @Test
    void list_delegueLeControleDaccesEtLaVerificationDexistenceACommercialService() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable janvier = mois(10, 1, "Janvier 2026");
        MoisComptable fevrier = mois(11, 2, "Fevrier 2026");
        CommercialMonthlyObjective o1 = CommercialMonthlyObjective.builder().id(1L).commercial(c).moisComptable(janvier)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(5_000_000)).build();
        CommercialMonthlyObjective o2 = CommercialMonthlyObjective.builder().id(2L).commercial(c).moisComptable(fevrier)
                .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(6_000_000)).build();
        when(fx.repository().findByCommercialIdOrderByMoisComptable_NumeroDesc(1L)).thenReturn(List.of(o2, o1));
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);

        List<CommercialMonthlyObjectiveResponse> objectifs = fx.service().list(1L);

        assertEquals(2, objectifs.size());
        verify(fx.commercialService()).verifierAccesCommercialCible(1L);
        verify(fx.commercialService()).getCommercial(1L);
    }
}
