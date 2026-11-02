package ci.assurzen.services;

import ci.assurzen.dto.InsuredRequest;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.TypeClient;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le garde-fou IDOR ajoute le 2026-09-12 (audit de securite) : avant ce correctif,
 * findById()/update()/delete()/search() n'avaient aucune verification d'acces. Scenarios directement
 * issus du §19 du contrat d'audit ("utilisateur du partenaire A tente d'acceder aux donnees du
 * partenaire B", "utilisateur modifie un ID dans l'URL").
 */
class InsuredServiceTest {

    private record Fixture(InsuredService service, InsuredRepository insuredRepository, ContractRepository contractRepository) {
    }

    private Fixture newFixture(String roles) {
        InsuredRepository insuredRepository = mock(InsuredRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(44L, "jean.kouassi.test", roles));
        return new Fixture(new InsuredService(insuredRepository, contractRepository, authService), insuredRepository, contractRepository);
    }

    private Insured insured(long id) {
        return Insured.builder().id(id).nom("KOUASSI").prenoms("Jean").typeClient(TypeClient.PARTICULIER).build();
    }

    // ---------------------------------------------------------------- findById() / update() / delete()

    @Test
    void findById_nonAdminAvecUnContratSurCetAssure_estAutorise() {
        Fixture fx = newFixture("COMMERCIAL");
        when(fx.contractRepository().existsByInsuredIdAndSalesRepId(1L, 44L)).thenReturn(true);
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));

        InsuredResponse response = fx.service().findById(1L);

        assertEquals(1L, response.id());
    }

    @Test
    void findById_nonAdminSansAucunContratSurCetAssure_estRejeteAvec404_pasDeFuiteDexistence() {
        // §19 : "utilisateur modifie un ID dans l'URL -> le controle d'autorisation reste applique".
        // 404 (pas 403) deliberement, meme convention que ContractsService.getContractScoped() - ne
        // pas confirmer a un tiers non autorise que l'id existe.
        Fixture fx = newFixture("COMMERCIAL");
        when(fx.contractRepository().existsByInsuredIdAndSalesRepId(2L, 44L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().findById(2L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(fx.insuredRepository(), never()).findById(2L);
    }

    @Test
    void findById_admin_accedeSansAucuneVerificationDeContrat() {
        Fixture fx = newFixture("ADMIN");
        when(fx.insuredRepository().findById(2L)).thenReturn(Optional.of(insured(2L)));

        InsuredResponse response = fx.service().findById(2L);

        assertEquals(2L, response.id());
        verify(fx.contractRepository(), never()).existsByInsuredIdAndSalesRepId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void update_nonAdminSansAcces_estRejeteAvec404_neSauvegardeRien() {
        Fixture fx = newFixture("USER");
        when(fx.contractRepository().existsByInsuredIdAndSalesRepId(5L, 44L)).thenReturn(false);
        InsuredRequest request = new InsuredRequest("X", "Y", null, null, null, TypeClient.PARTICULIER, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(5L, request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(fx.insuredRepository(), never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_nonAdminSansAcces_estRejeteAvec404_neSupprimeRien() {
        Fixture fx = newFixture("USER");
        when(fx.contractRepository().existsByInsuredIdAndSalesRepId(5L, 44L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(5L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(fx.insuredRepository(), never()).delete(org.mockito.ArgumentMatchers.any());
    }

    // ---------------------------------------------------------------- search() (scoping de la liste)

    @Test
    void search_nonAdmin_neVoitQueLesAssuresLiesASesPropresContrats() {
        // §19 : un utilisateur ne doit pas pouvoir contourner le filtre en listant plutot qu'en
        // consultant un id precis - la meme regle de perimetre doit s'appliquer aux deux.
        Fixture fx = newFixture("COMMERCIAL");
        Contract monContrat = Contract.builder().id(66L).insured(insured(1L)).build();
        when(fx.contractRepository().findBySalesRepIdOrderByCreatedAtDesc(44L)).thenReturn(List.of(monContrat));
        when(fx.insuredRepository().findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(insured(1L), insured(2L), insured(3L)));

        PageResponse<InsuredResponse> response = fx.service().search(null, null, 0, 20);

        assertEquals(1, response.totalElements());
        assertEquals(1L, response.content().get(0).id());
    }

    @Test
    void search_admin_voitTousLesAssures() {
        Fixture fx = newFixture("ADMIN");
        when(fx.insuredRepository().findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(insured(1L), insured(2L), insured(3L)));

        PageResponse<InsuredResponse> response = fx.service().search(null, null, 0, 20);

        assertEquals(3, response.totalElements());
        verify(fx.contractRepository(), never()).findBySalesRepIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong());
    }
}
