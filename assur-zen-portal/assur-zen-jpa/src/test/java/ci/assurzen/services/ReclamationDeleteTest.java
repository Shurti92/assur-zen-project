package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.Reclamation;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.ReclamationCommentaireRepository;
import ci.assurzen.repositories.ReclamationPieceJointeRepository;
import ci.assurzen.repositories.ReclamationRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre le nouveau DELETE /reclamations/{id} (docs/api-contracts/reclamations-delete.md) : reserve
 * a la purge d'un enregistrement de test/doublon sans historique metier - jamais un contournement de
 * la cloture pour un vrai dossier deja engage (commentaire, piece jointe, ou creation automatique
 * suite a un score NPS bas). */
class ReclamationDeleteTest {

    private record Fixture(
            ReclamationService service,
            ReclamationRepository reclamationRepository,
            ReclamationCommentaireRepository reclamationCommentaireRepository,
            ReclamationPieceJointeRepository reclamationPieceJointeRepository,
            AuthService authService
    ) {
    }

    private Fixture newFixture() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        ReclamationCommentaireRepository reclamationCommentaireRepository = mock(ReclamationCommentaireRepository.class);
        ReclamationPieceJointeRepository reclamationPieceJointeRepository = mock(ReclamationPieceJointeRepository.class);
        InsuredRepository insuredRepository = mock(InsuredRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        ProduitRepository produitRepository = mock(ProduitRepository.class);
        SinistreRepository sinistreRepository = mock(SinistreRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        AuthService authService = mock(AuthService.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        ReclamationStorageService reclamationStorageService = mock(ReclamationStorageService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);

        ReclamationService service = new ReclamationService(reclamationRepository, reclamationCommentaireRepository,
                reclamationPieceJointeRepository, insuredRepository, contractRepository, produitRepository,
                sinistreRepository, userRepository, profileRepository, authService, codeGeneratorService,
                reclamationStorageService, smsNotificationService, functionalAuditRecorder, dynamicConfigurationService);

        return new Fixture(service, reclamationRepository, reclamationCommentaireRepository,
                reclamationPieceJointeRepository, authService);
    }

    private void stubManager(AuthService authService) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
    }

    private Reclamation reclamation(long id, boolean creeeAutomatiquement) {
        return Reclamation.builder().id(id).numero("REC2026000001").description("Test").creeeAutomatiquement(creeeAutomatiquement).build();
    }

    @Test
    void delete_nonAutorise_estRejeteAvec403() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "0700000000", "USER"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.reclamationRepository(), never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_reclamationInexistante_renvoie404() {
        Fixture fx = newFixture();
        stubManager(fx.authService());
        when(fx.reclamationRepository().findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void delete_creeeAutomatiquement_estRejeteAvec409() {
        Fixture fx = newFixture();
        stubManager(fx.authService());
        when(fx.reclamationRepository().findById(1L)).thenReturn(Optional.of(reclamation(1L, true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.reclamationRepository(), never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_avecCommentaires_estRejeteAvec409() {
        Fixture fx = newFixture();
        stubManager(fx.authService());
        when(fx.reclamationRepository().findById(1L)).thenReturn(Optional.of(reclamation(1L, false)));
        when(fx.reclamationCommentaireRepository().existsByReclamationId(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.reclamationRepository(), never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_avecPiecesJointes_estRejeteAvec409() {
        Fixture fx = newFixture();
        stubManager(fx.authService());
        when(fx.reclamationRepository().findById(1L)).thenReturn(Optional.of(reclamation(1L, false)));
        when(fx.reclamationCommentaireRepository().existsByReclamationId(1L)).thenReturn(false);
        when(fx.reclamationPieceJointeRepository().existsByReclamationId(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.reclamationRepository(), never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_casNominal_supprimeLaReclamation() {
        Fixture fx = newFixture();
        stubManager(fx.authService());
        Reclamation reclamation = reclamation(1L, false);
        when(fx.reclamationRepository().findById(1L)).thenReturn(Optional.of(reclamation));
        when(fx.reclamationCommentaireRepository().existsByReclamationId(1L)).thenReturn(false);
        when(fx.reclamationPieceJointeRepository().existsByReclamationId(1L)).thenReturn(false);

        fx.service().delete(1L);

        verify(fx.reclamationRepository()).delete(reclamation);
    }
}
