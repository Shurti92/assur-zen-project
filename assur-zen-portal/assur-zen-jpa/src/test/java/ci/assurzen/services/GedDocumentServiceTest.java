package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentLinkRequest;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.dto.GedDocumentVersionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.UpdateGedDocumentRequest;
import ci.assurzen.entities.GedConfidentialityLevel;
import ci.assurzen.entities.GedDocument;
import ci.assurzen.entities.GedDocumentCategory;
import ci.assurzen.entities.GedDocumentLink;
import ci.assurzen.entities.GedDocumentStatus;
import ci.assurzen.entities.GedDocumentType;
import ci.assurzen.entities.GedDocumentVersion;
import ci.assurzen.repositories.GedDocumentLinkRepository;
import ci.assurzen.repositories.GedDocumentRepository;
import ci.assurzen.repositories.GedDocumentVersionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre le noyau du module GED (Phase 1) : creation + version 1 + lien optionnel, signalement de
 * doublon non bloquant, validations de fichier (taille/extension), append-only des versions,
 * transitions de statut, telechargement, recherche par lien generique. */
class GedDocumentServiceTest {

    private record Fixture(
            GedDocumentService service,
            GedDocumentRepository documentRepository,
            GedDocumentVersionRepository versionRepository,
            GedDocumentLinkRepository linkRepository,
            GedDocumentTypeService documentTypeService,
            GedDocumentCategoryService documentCategoryService,
            DocumentStorageService documentStorageService,
            DocumentSecurityScanner documentSecurityScanner,
            DynamicConfigurationService dynamicConfigurationService,
            FunctionalAuditRecorder functionalAuditRecorder,
            AuthService authService
    ) {
    }

    private Fixture newFixture() {
        GedDocumentRepository documentRepository = mock(GedDocumentRepository.class);
        GedDocumentVersionRepository versionRepository = mock(GedDocumentVersionRepository.class);
        GedDocumentLinkRepository linkRepository = mock(GedDocumentLinkRepository.class);
        GedDocumentTypeService documentTypeService = mock(GedDocumentTypeService.class);
        GedDocumentCategoryService documentCategoryService = mock(GedDocumentCategoryService.class);
        DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
        DocumentSecurityScanner documentSecurityScanner = mock(DocumentSecurityScanner.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        AuthService authService = mock(AuthService.class);

        when(dynamicConfigurationService.getLong(anyString(), any())).thenReturn(10L * 1024 * 1024);
        when(dynamicConfigurationService.getString(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(documentSecurityScanner.scan(any()))
                .thenReturn(new DocumentSecurityScanner.ScanResult(DocumentSecurityScanner.ScanStatus.CLEAN, "ok"));
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(codeGeneratorService.generate(any())).thenReturn("GED2026000001");

        AtomicLong documentIdSeq = new AtomicLong(1);
        when(documentRepository.save(any())).thenAnswer(inv -> {
            GedDocument d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(documentIdSeq.getAndIncrement());
            }
            return d;
        });
        AtomicLong versionIdSeq = new AtomicLong(1);
        when(versionRepository.save(any())).thenAnswer(inv -> {
            GedDocumentVersion v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(versionIdSeq.getAndIncrement());
            }
            return v;
        });
        AtomicLong linkIdSeq = new AtomicLong(1);
        when(linkRepository.save(any())).thenAnswer(inv -> {
            GedDocumentLink l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(linkIdSeq.getAndIncrement());
            }
            return l;
        });
        when(linkRepository.findByDocumentIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        GedDocumentService service = new GedDocumentService(documentRepository, versionRepository, linkRepository,
                documentTypeService, documentCategoryService, documentStorageService, documentSecurityScanner,
                codeGeneratorService, dynamicConfigurationService, functionalAuditRecorder, authService);

        return new Fixture(service, documentRepository, versionRepository, linkRepository, documentTypeService,
                documentCategoryService, documentStorageService, documentSecurityScanner,
                dynamicConfigurationService, functionalAuditRecorder, authService);
    }

    private GedDocumentType documentType(long id, String code) {
        return GedDocumentType.builder().id(id).code(code).nom(code).build();
    }

    private GedDocumentCategory category(long id, String code) {
        return GedDocumentCategory.builder().id(id).code(code).nom(code).build();
    }

    private MultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "contenu".getBytes());
    }

    // ---------------------------------------------------------------- Creation

    @Test
    void create_avecLienMetier_creeDocumentVersion1EtLien() {
        Fixture fx = newFixture();
        when(fx.documentTypeService().getEntity(10L)).thenReturn(documentType(10L, "CONTRAT"));
        when(fx.documentStorageService().store(any(), any(), any()))
                .thenReturn(new StorageResult("/disk/path", "stored.pdf", 7L, "checksum-a"));
        when(fx.documentRepository().findFirstByChecksumSha256OrderByCreatedAtAsc("checksum-a")).thenReturn(Optional.empty());
        when(fx.versionRepository().findFirstByDocumentIdOrderByVersionNumberDesc(any()))
                .thenReturn(Optional.of(GedDocumentVersion.builder().versionNumber(1).build()));

        GedDocumentResponse response = fx.service().create(10L, null, "Contrat CI-1234", null,
                "CONTRACT", 458L, null, null, pdf("contrat.pdf"));

        assertEquals(GedDocumentStatus.UPLOADED, response.status());
        assertEquals(GedConfidentialityLevel.INTERNAL, response.confidentialityLevel());
        assertEquals(1, response.currentVersionNumber());
        assertNull(response.duplicateOfDocumentId());
        verify(fx.linkRepository()).save(any());
        verify(fx.functionalAuditRecorder()).recordAction(eq("GED"), eq("GedDocument"), any(), any(), any());
    }

    @Test
    void create_sansTitre_estRejeteAvec400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(10L, null, "  ", null, null, null, null, null, pdf("x.pdf")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_checksumDejaConnu_signaleLeDoublonSansBloquer() {
        Fixture fx = newFixture();
        when(fx.documentTypeService().getEntity(10L)).thenReturn(documentType(10L, "CONTRAT"));
        when(fx.documentStorageService().store(any(), any(), any()))
                .thenReturn(new StorageResult("/disk/path2", "stored2.pdf", 7L, "checksum-existant"));
        GedDocument original = GedDocument.builder().id(99L).checksumSha256("checksum-existant").build();
        when(fx.documentRepository().findFirstByChecksumSha256OrderByCreatedAtAsc("checksum-existant"))
                .thenReturn(Optional.of(original));

        GedDocumentResponse response = fx.service().create(10L, null, "Doublon", null, null, null, null, null, pdf("x.pdf"));

        assertEquals(99L, response.duplicateOfDocumentId());
        assertEquals(GedDocumentStatus.UPLOADED, response.status());
    }

    @Test
    void create_fichierTropVolumineux_estRejeteAvec400() {
        Fixture fx = newFixture();
        when(fx.dynamicConfigurationService().getLong(anyString(), any())).thenReturn(5L);
        when(fx.documentTypeService().getEntity(10L)).thenReturn(documentType(10L, "CONTRAT"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(10L, null, "Titre", null, null, null, null, null, pdf("gros.pdf")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(fx.documentStorageService(), never()).store(any(), any(), any());
    }

    @Test
    void create_extensionHorsWhitelist_estRejeteAvec415() {
        Fixture fx = newFixture();
        when(fx.documentTypeService().getEntity(10L)).thenReturn(documentType(10L, "CONTRAT"));
        MultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "x".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(10L, null, "Titre", null, null, null, null, null, exe));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
        verify(fx.documentStorageService(), never()).store(any(), any(), any());
    }

    // ---------------------------------------------------------------- Versions

    @Test
    void addVersion_incrementeSansToucherLesVersionsPrecedentes() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).uuid("uuid-1").documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).status(GedDocumentStatus.UPLOADED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));
        when(fx.versionRepository().findFirstByDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(GedDocumentVersion.builder().id(1L).versionNumber(1).build()));
        when(fx.documentStorageService().store(any(), any(), any()))
                .thenReturn(new StorageResult("/disk/v2", "stored-v2.pdf", 9L, "checksum-v2"));

        GedDocumentVersionResponse response = fx.service().addVersion(1L, pdf("avenant.pdf"));

        assertEquals(2, response.versionNumber());
        verify(fx.versionRepository(), never()).delete(any());
        verify(fx.versionRepository(), never()).deleteById(any());
    }

    @Test
    void addVersion_surDocumentArchive_estRejeteAvec409() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).status(GedDocumentStatus.ARCHIVED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVersion(1L, pdf("x.pdf")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.documentStorageService(), never()).store(any(), any(), any());
    }

    @Test
    void download_sansVersionId_renvoieLaDerniereVersion() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001").build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));
        GedDocumentVersion latest = GedDocumentVersion.builder().id(2L).versionNumber(2)
                .storedPath("/disk/v2").originalFileName("v2.pdf").mimeType("application/pdf").build();
        when(fx.versionRepository().findFirstByDocumentIdOrderByVersionNumberDesc(1L)).thenReturn(Optional.of(latest));
        when(fx.documentStorageService().read("/disk/v2")).thenReturn("contenu-v2".getBytes());

        GedDocumentVersionContent content = fx.service().download(1L);

        assertEquals("v2.pdf", content.fileName());
        verify(fx.functionalAuditRecorder()).recordAction(eq("GED"), eq("GedDocument"), any(), any(), any());
    }

    @Test
    void download_avecVersionId_renvoieLaVersionPrecise() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001").build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));
        GedDocumentVersion v1 = GedDocumentVersion.builder().id(1L).versionNumber(1)
                .storedPath("/disk/v1").originalFileName("v1.pdf").mimeType("application/pdf").build();
        when(fx.versionRepository().findByIdAndDocumentId(1L, 1L)).thenReturn(Optional.of(v1));
        when(fx.documentStorageService().read("/disk/v1")).thenReturn("contenu-v1".getBytes());

        GedDocumentVersionContent content = fx.service().downloadVersion(1L, 1L);

        assertEquals("v1.pdf", content.fileName());
    }

    // ---------------------------------------------------------------- Transitions

    @Test
    void validate_documentUploaded_passeAuStatutValide() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).status(GedDocumentStatus.UPLOADED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        GedDocumentResponse response = fx.service().validate(1L);

        assertEquals(GedDocumentStatus.VALIDATED, response.status());
        verify(fx.functionalAuditRecorder()).recordAction(eq("GED"), eq("GedDocument"), any(), any(), any());
    }

    @Test
    void validate_documentDejaArchive_estRejeteAvec409() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001").status(GedDocumentStatus.ARCHIVED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().validate(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void validate_documentDejaValide_estRejeteAvec409() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001").status(GedDocumentStatus.VALIDATED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().validate(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void reject_sansMotif_estRejeteAvec400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().reject(1L, "  "));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(fx.documentRepository(), never()).findById(any());
    }

    @Test
    void reject_avecMotif_passeAuStatutRejeteEtEnregistreLeMotif() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).status(GedDocumentStatus.UPLOADED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        GedDocumentResponse response = fx.service().reject(1L, "Document illisible");

        assertEquals(GedDocumentStatus.REJECTED, response.status());
        assertEquals("Document illisible", response.rejectionReason());
        verify(fx.functionalAuditRecorder()).recordAction(eq("GED"), eq("GedDocument"), any(), any(), eq("Document illisible"));
    }

    @Test
    void archive_puisAjouterVersion_estRejeteAvec409() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).status(GedDocumentStatus.VALIDATED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        GedDocumentResponse archived = fx.service().archive(1L);
        assertEquals(GedDocumentStatus.ARCHIVED, archived.status());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().addVersion(1L, pdf("x.pdf")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void delete_estUneSuppressionLogique() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001").status(GedDocumentStatus.UPLOADED).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));

        fx.service().delete(1L);

        assertTrue(document.isDeleted());
        assertEquals(GedDocumentStatus.DELETED, document.getStatus());
        verify(fx.documentRepository(), never()).delete(any());
        verify(fx.documentRepository(), never()).deleteById(any());
    }

    // ---------------------------------------------------------------- Liens / recherche

    @Test
    void addLink_dejaExistant_estRejeteAvec409() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));
        GedDocumentLink existing = GedDocumentLink.builder().id(5L).document(document).entityType("CONTRACT").entityId(458L).build();
        when(fx.linkRepository().findByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addLink(1L, new GedDocumentLinkRequest("CONTRACT", 458L, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void listByEntity_retrouveLesDocumentsRattaches() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).status(GedDocumentStatus.UPLOADED)
                .confidentialityLevel(GedConfidentialityLevel.INTERNAL).build();
        document.setCreatedAt(java.time.Instant.now());
        GedDocumentLink link = GedDocumentLink.builder().id(1L).document(document).entityType("CONTRACT").entityId(458L).build();
        when(fx.linkRepository().findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CONTRACT", 458L)).thenReturn(List.of(link));
        when(fx.documentRepository().findAllById(any())).thenReturn(List.of(document));

        PageResponse<GedDocumentResponse> page = fx.service().listByEntity("CONTRACT", 458L, 0, 20);

        assertEquals(1, page.totalElements());
        assertEquals("GED2026000001", page.content().get(0).documentNumber());
    }

    @Test
    void update_modifieLesMetadonneesSeules() {
        Fixture fx = newFixture();
        GedDocument document = GedDocument.builder().id(1L).documentNumber("GED2026000001")
                .documentType(documentType(10L, "CONTRAT")).title("Ancien titre")
                .status(GedDocumentStatus.UPLOADED).confidentialityLevel(GedConfidentialityLevel.INTERNAL).build();
        when(fx.documentRepository().findById(1L)).thenReturn(Optional.of(document));
        when(fx.documentCategoryService().getEntity(20L)).thenReturn(category(20L, "CONTRACTUAL"));

        GedDocumentResponse response = fx.service().update(1L,
                new UpdateGedDocumentRequest("Nouveau titre", "desc", 20L, GedConfidentialityLevel.CONFIDENTIAL));

        assertEquals("Nouveau titre", response.title());
        assertEquals("CONTRACTUAL", response.categoryCode());
        assertEquals(GedConfidentialityLevel.CONFIDENTIAL, response.confidentialityLevel());
    }
}
