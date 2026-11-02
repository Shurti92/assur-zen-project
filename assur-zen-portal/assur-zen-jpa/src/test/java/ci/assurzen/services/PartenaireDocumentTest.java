package ci.assurzen.services;

import ci.assurzen.dto.PartenaireDocumentContent;
import ci.assurzen.dto.PartenaireDocumentResponse;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireDocument;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.repositories.PartenaireDocumentRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.TypeProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre le nouveau point d'API "documents joints a un partenaire" (contrat d'apporteur, CNI,
 * RIB...), demande cross-equipe (docs/api-contracts/partenaire-documents.md), calque sur
 * ReclamationPieceJointe/SinistrePhoto. Le point le plus sensible : verifierAccesPartenaireCible
 * (garde-fou IDOR confirme en audit le 2026-08-23 sur GET /partenaires/{id}) doit s'appliquer
 * IDENTIQUEMENT aux 3 nouveaux endpoints - sans quoi n'importe quel compte partenaire pourrait
 * lister/uploader/telecharger les documents (CNI, RIB...) de N'IMPORTE QUEL AUTRE partenaire.
 */
class PartenaireDocumentTest {

    private record Fixture(
            PartenaireService service,
            PartenaireRepository repository,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            AuthService authService,
            PartenaireDocumentRepository partenaireDocumentRepository,
            PartenaireDocumentStorageService partenaireDocumentStorageService,
            DynamicConfigurationService dynamicConfigurationService
    ) {
    }

    private Fixture newFixture() {
        PartenaireRepository repository = mock(PartenaireRepository.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        ExerciceComptableRepository exerciceComptableRepository = mock(ExerciceComptableRepository.class);
        VillePartenaireRepository villePartenaireRepository = mock(VillePartenaireRepository.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        TypeProfileRepository typeProfileRepository = mock(TypeProfileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        AuthService authService = mock(AuthService.class);
        PasswordHistoryService passwordHistoryService = mock(PasswordHistoryService.class);
        PartenaireDocumentRepository partenaireDocumentRepository = mock(PartenaireDocumentRepository.class);
        PartenaireDocumentStorageService partenaireDocumentStorageService = mock(PartenaireDocumentStorageService.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);

        PartenaireService service = new PartenaireService(repository, profileRepository, contractRepository,
                exerciceComptableRepository, villePartenaireRepository, codeGeneratorService, typeProfileRepository,
                userRepository, passwordEncoder, smsNotificationService, userProfileService, authService,
                passwordHistoryService, partenaireDocumentRepository, partenaireDocumentStorageService,
                dynamicConfigurationService, mock(org.springframework.context.ApplicationEventPublisher.class));

        return new Fixture(service, repository, profileRepository, userRepository, authService,
                partenaireDocumentRepository, partenaireDocumentStorageService, dynamicConfigurationService);
    }

    private void stubAdmin(AuthService authService) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
    }

    /** Compte "partenaire" (role USER) lie a un AUTRE partenaire que celui vise. */
    private void stubNonProprietaire(AuthService authService, ProfileRepository profileRepository, PartenaireRepository repository) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "0700000000", "USER"));
        Profile profile = mock(Profile.class);
        when(profile.getId()).thenReturn(99L);
        when(profileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(repository.findByProfile_Id(99L)).thenReturn(List.of(Partenaire.builder().id(999L).build()));
    }

    private void stubProprietaire(AuthService authService, ProfileRepository profileRepository, PartenaireRepository repository, Long partenaireId) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "0700000000", "USER"));
        Profile profile = mock(Profile.class);
        when(profile.getId()).thenReturn(99L);
        when(profileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(repository.findByProfile_Id(99L)).thenReturn(List.of(Partenaire.builder().id(partenaireId).build()));
    }

    @Test
    void listDocuments_nonAdminEtPasProprietaire_estRejeteAvec403() {
        Fixture fx = newFixture();
        stubNonProprietaire(fx.authService(), fx.profileRepository(), fx.repository());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().listDocuments(42L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void ajouterDocument_nonAdminEtPasProprietaire_estRejeteAvec403() {
        Fixture fx = newFixture();
        stubNonProprietaire(fx.authService(), fx.profileRepository(), fx.repository());
        MultipartFile file = new MockMultipartFile("file", "cni.pdf", "application/pdf", "contenu".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().ajouterDocument(42L, "CNI", file));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getDocumentContent_nonAdminEtPasProprietaire_estRejeteAvec403() {
        Fixture fx = newFixture();
        stubNonProprietaire(fx.authService(), fx.profileRepository(), fx.repository());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().getDocumentContent(42L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void listDocuments_proprietaireDuPartenaire_estAutorise() {
        Fixture fx = newFixture();
        stubProprietaire(fx.authService(), fx.profileRepository(), fx.repository(), 42L);
        when(fx.repository().findById(42L)).thenReturn(Optional.of(Partenaire.builder().id(42L).build()));
        when(fx.partenaireDocumentRepository().findByPartenaireIdOrderByCreatedAtAsc(42L)).thenReturn(List.of());

        List<PartenaireDocumentResponse> result = fx.service().listDocuments(42L);

        assertEquals(0, result.size());
    }

    @Test
    void ajouterDocument_fichierVide_estRejeteAvec400() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        MultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().ajouterDocument(42L, "CNI", emptyFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void ajouterDocument_typeManquant_estRejeteAvec400() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        MultipartFile file = new MockMultipartFile("file", "cni.pdf", "application/pdf", "contenu".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().ajouterDocument(42L, "  ", file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void ajouterDocument_admin_casNominal_creeUnDocumentAvecSonType() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        Partenaire partenaire = Partenaire.builder().id(42L).build();
        when(fx.repository().findById(42L)).thenReturn(Optional.of(partenaire));
        when(fx.dynamicConfigurationService().getLong("MAX_FILE_SIZE", 10L * 1024 * 1024)).thenReturn(10L * 1024 * 1024);
        when(fx.partenaireDocumentStorageService().store(eqLong(42L), any(), any())).thenReturn("/uploads/partenaires/42/uuid-cni.pdf");
        User uploader = new User();
        uploader.setId(1L);
        uploader.setUsername("admin");
        when(fx.userRepository().findById(1L)).thenReturn(Optional.of(uploader));
        when(fx.partenaireDocumentRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        MultipartFile file = new MockMultipartFile("file", "cni_kouassi.pdf", "application/pdf", "%PDF-1.4 contenu".getBytes());

        PartenaireDocumentResponse response = fx.service().ajouterDocument(42L, "CNI", file);

        assertEquals("CNI", response.type());
        assertEquals(42L, response.partenaireId());
        assertEquals("cni_kouassi.pdf", response.fileName());

        ArgumentCaptor<PartenaireDocument> captor = ArgumentCaptor.forClass(PartenaireDocument.class);
        org.mockito.Mockito.verify(fx.partenaireDocumentRepository()).save(captor.capture());
        assertEquals("CNI", captor.getValue().getType());
    }

    @Test
    void getDocumentContent_documentInexistant_renvoie404() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        when(fx.partenaireDocumentRepository().findByIdAndPartenaireId(1L, 42L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().getDocumentContent(42L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteDocument_nonAdminEtPasProprietaire_estRejeteAvec403() {
        Fixture fx = newFixture();
        stubNonProprietaire(fx.authService(), fx.profileRepository(), fx.repository());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().deleteDocument(42L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        org.mockito.Mockito.verify(fx.partenaireDocumentRepository(), org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteDocument_documentInexistant_renvoie404() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        when(fx.partenaireDocumentRepository().findByIdAndPartenaireId(1L, 42L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().deleteDocument(42L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteDocument_casNominal_supprimeLaLigneEtLeFichier() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        PartenaireDocument document = PartenaireDocument.builder().id(1L).storedPath("/uploads/partenaires/42/uuid-cni.pdf").build();
        when(fx.partenaireDocumentRepository().findByIdAndPartenaireId(1L, 42L)).thenReturn(Optional.of(document));

        fx.service().deleteDocument(42L, 1L);

        org.mockito.Mockito.verify(fx.partenaireDocumentRepository()).delete(document);
        org.mockito.Mockito.verify(fx.partenaireDocumentStorageService()).delete("/uploads/partenaires/42/uuid-cni.pdf");
    }

    private static Long eqLong(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    // ---- Statut de validation des documents (2026-10-04) ----

    @Test
    void validerDocument_nonAdmin_403() {
        Fixture fx = newFixture();
        stubProprietaire(fx.authService(), fx.profileRepository(), fx.repository(), 42L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().validerDocument(42L, 5L,
                ci.assurzen.entities.StatutValidationDocument.VALIDE, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void validerDocument_refusSansMotif_400_statutEnAttente_400() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> fx.service().validerDocument(42L, 5L,
                ci.assurzen.entities.StatutValidationDocument.REFUSE, "  ")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> fx.service().validerDocument(42L, 5L,
                ci.assurzen.entities.StatutValidationDocument.EN_ATTENTE, null)).getStatusCode());
    }

    @Test
    void validerDocument_refus_motifEtDateRenseignes_documentAnterieurEnAttenteParDefaut() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        PartenaireDocument document = PartenaireDocument.builder().id(5L).type("CNI").fileName("cni.pdf")
                .partenaire(Partenaire.builder().id(42L).build()).build();
        when(fx.partenaireDocumentRepository().findByIdAndPartenaireId(5L, 42L)).thenReturn(Optional.of(document));
        when(fx.partenaireDocumentRepository().save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        ci.assurzen.entities.User admin = new ci.assurzen.entities.User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(fx.userRepository().findById(1L)).thenReturn(Optional.of(admin));

        when(fx.repository().findById(42L)).thenReturn(Optional.of(document.getPartenaire()));
        when(fx.partenaireDocumentRepository().findByPartenaireIdOrderByCreatedAtAsc(42L)).thenReturn(List.of(document));
        assertEquals(ci.assurzen.entities.StatutValidationDocument.EN_ATTENTE, fx.service().listDocuments(42L).get(0).statut());

        PartenaireDocumentResponse reponse = fx.service().validerDocument(42L, 5L,
                ci.assurzen.entities.StatutValidationDocument.REFUSE, " Photo illisible ");

        assertEquals(ci.assurzen.entities.StatutValidationDocument.REFUSE, reponse.statut());
        assertEquals("Photo illisible", reponse.motifRefus());
        org.junit.jupiter.api.Assertions.assertNotNull(reponse.valideLe());
    }

    // ---- Controle des fichiers (2026-10-05) ----

    @Test
    void ajouterDocument_executableDeguise_refuseAvec400_rienNEstEnregistre() {
        Fixture fx = newFixture();
        stubAdmin(fx.authService());
        MultipartFile exe = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "MZ".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().ajouterDocument(42L, "CNI", exe));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(fx.partenaireDocumentStorageService());
    }
}
