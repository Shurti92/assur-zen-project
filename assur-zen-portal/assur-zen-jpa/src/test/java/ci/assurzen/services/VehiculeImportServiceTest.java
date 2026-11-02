package ci.assurzen.services;

import ci.assurzen.dto.FleetImportResponse;
import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.FleetImport;
import ci.assurzen.entities.FleetImportFormat;
import ci.assurzen.entities.FleetImportStatus;
import ci.assurzen.entities.FleetStatus;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.FleetImportErrorRepository;
import ci.assurzen.repositories.FleetImportRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre l'orchestration de l'import de vehicules (Phase 2) : dedoublonnage par checksum, machine a
 * etats READY/PROCESSING/COMPLETED/PARTIALLY_COMPLETED/FAILED, isolation des echecs ligne par ligne.
 * VehiculeImportRowProcessor (transaction REQUIRES_NEW par ligne, deja couvert indirectement via
 * FleetService.ajouterVehiculeAuCoeur dans FleetServiceTest) est mocke ici - ce test se concentre sur
 * la couche d'orchestration, pas sur les regles d'eligibilite d'un vehicule (deja testees Phase 1). */
class VehiculeImportServiceTest {

    private record Fixture(
            VehiculeImportService service,
            FleetImportRepository fleetImportRepository,
            FleetImportErrorRepository fleetImportErrorRepository,
            VehiculeImportStorageService vehiculeImportStorageService,
            VehiculeImportRowProcessor vehiculeImportRowProcessor,
            UserRepository userRepository,
            AuthService authService,
            DynamicConfigurationService dynamicConfigurationService,
            FleetService fleetService
    ) {
    }

    private Fixture newFixture() {
        FleetImportRepository fleetImportRepository = mock(FleetImportRepository.class);
        FleetImportErrorRepository fleetImportErrorRepository = mock(FleetImportErrorRepository.class);
        VehiculeImportStorageService vehiculeImportStorageService = mock(VehiculeImportStorageService.class);
        VehiculeImportRowProcessor vehiculeImportRowProcessor = mock(VehiculeImportRowProcessor.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuthService authService = mock(AuthService.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        FleetService fleetService = mock(FleetService.class);

        // Mockito renvoie 0 (pas null) par defaut pour un Long non stubbe - sans ce defaut explicite,
        // toute taille de fichier non nulle serait rejetee a tort comme "trop volumineuse".
        when(dynamicConfigurationService.getLong(any(), any())).thenReturn(10L * 1024 * 1024);

        VehiculeImportService service = new VehiculeImportService(fleetImportRepository, fleetImportErrorRepository,
                vehiculeImportStorageService, vehiculeImportRowProcessor, userRepository, authService,
                dynamicConfigurationService, fleetService);

        return new Fixture(service, fleetImportRepository, fleetImportErrorRepository, vehiculeImportStorageService,
                vehiculeImportRowProcessor, userRepository, authService, dynamicConfigurationService, fleetService);
    }

    private Fleet fleet(long id, FleetStatus status) {
        return Fleet.builder().id(id).fleetNumber("FLT2026000001").name("Flotte test").status(status).build();
    }

    private void stubUser(AuthService authService, UserRepository userRepository) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private MultipartFile csvValide() {
        String contenu = "immatriculation,type,marque,modele\n"
                + "CI-1234-AA,AUTO,Toyota,Corolla\n"
                + "CI-5678-BB,MOTO,Yamaha,MT07\n";
        return new MockMultipartFile("file", "vehicules.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    private MultipartFile csvEntierementInvalide() {
        String contenu = "immatriculation,type,marque,modele\n,CAMION,,\n";
        return new MockMultipartFile("file", "vehicules.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preview_fichierDejaImporte_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE);
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.fleetImportRepository().findByFleetIdAndFileChecksumAndStatusIn(eq(1L), any(), any()))
                .thenReturn(Optional.of(FleetImport.builder().id(99L).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().preview(1L, csvValide()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void preview_fichierEntierementInvalide_creeUnImportFailed() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE);
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.fleetImportRepository().findByFleetIdAndFileChecksumAndStatusIn(eq(1L), any(), any())).thenReturn(Optional.empty());
        when(fx.vehiculeImportStorageService().store(eq(1L), any(), any())).thenReturn("/uploads/fleet-imports/1/uuid.csv");
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> {
            FleetImport imp = inv.getArgument(0);
            imp.setId(1L);
            return imp;
        });
        stubUser(fx.authService(), fx.userRepository());

        FleetImportResponse response = fx.service().preview(1L, csvEntierementInvalide());

        assertEquals(FleetImportStatus.FAILED, response.status());
        assertEquals(0, response.validRows());
    }

    @Test
    void preview_fichierPartiellementValide_creeUnImportReady() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE);
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.fleetImportRepository().findByFleetIdAndFileChecksumAndStatusIn(eq(1L), any(), any())).thenReturn(Optional.empty());
        when(fx.vehiculeImportStorageService().store(eq(1L), any(), any())).thenReturn("/uploads/fleet-imports/1/uuid.csv");
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> {
            FleetImport imp = inv.getArgument(0);
            imp.setId(1L);
            return imp;
        });
        stubUser(fx.authService(), fx.userRepository());

        FleetImportResponse response = fx.service().preview(1L, csvValide());

        assertEquals(FleetImportStatus.READY, response.status());
        assertEquals(2, response.validRows());
        assertEquals(2, response.totalRows());
    }

    private FleetImport importReady(long fleetId) {
        Fleet fleet = fleet(fleetId, FleetStatus.ACTIVE);
        return FleetImport.builder()
                .id(10L)
                .fleet(fleet)
                .fileName("vehicules.csv")
                .storedPath("/uploads/fleet-imports/1/uuid.csv")
                .fileChecksum("abc")
                .format(FleetImportFormat.CSV)
                .status(FleetImportStatus.READY)
                .totalRows(2)
                .validRows(2)
                .invalidRows(0)
                .duplicateRows(0)
                .build();
    }

    @Test
    void confirm_importPasEnStatutReady_estRejeteAvec409() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        imp.setStatus(FleetImportStatus.COMPLETED);
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().confirm(1L, 10L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void confirm_toutesLesLignesReussissent_statutCompleted() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        String contenu = "immatriculation,type,marque,modele\nCI-1234-AA,AUTO,Toyota,Corolla\nCI-5678-BB,MOTO,Yamaha,MT07\n";
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));
        when(fx.fleetService().resolveFleet(1L)).thenReturn(imp.getFleet());
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.vehiculeImportStorageService().read("/uploads/fleet-imports/1/uuid.csv")).thenReturn(contenu.getBytes(StandardCharsets.UTF_8));
        doNothing().when(fx.vehiculeImportRowProcessor()).importerLigne(eq(1L), any());
        when(fx.fleetService().notifierIncorporationApresImport(1L, 2)).thenReturn(true);

        FleetImportResponse response = fx.service().confirm(1L, 10L);

        assertEquals(FleetImportStatus.COMPLETED, response.status());
        assertEquals(2, response.successCount());
        assertEquals(0, response.errorCount());
        verify(fx.fleetService()).recomputeCounters(imp.getFleet());
        // Un SEUL SMS agrege pour tout le lot (nombreVehicules=2), jamais un SMS par ligne importee
        // (§ "SMS d'incorporation flotte", 2026-09-08). Passe par notifierIncorporationApresImport(fleetId, ...)
        // et non notifierIncorporation(fleet, ...) directement : confirm() n'est pas transactionnel, "fleet"
        // y est detache a ce stade - un appel direct levait un vrai LazyInitializationException des qu'une
        // flotte sans managerPhone forcait le repli sur fleet.getClient().getClientPhone() (bug du 2026-09-13).
        verify(fx.fleetService()).notifierIncorporationApresImport(1L, 2);
        assertEquals(Boolean.TRUE, response.notificationEnvoyee());
    }

    @Test
    void confirm_certainesLignesEchouent_statutPartiallyCompletedEtErreursEnregistrees() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        String contenu = "immatriculation,type,marque,modele\nCI-1234-AA,AUTO,Toyota,Corolla\nCI-5678-BB,MOTO,Yamaha,MT07\n";
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));
        when(fx.fleetService().resolveFleet(1L)).thenReturn(imp.getFleet());
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.vehiculeImportStorageService().read("/uploads/fleet-imports/1/uuid.csv")).thenReturn(contenu.getBytes(StandardCharsets.UTF_8));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule deja actif dans une flotte (id 2)"))
                .when(fx.vehiculeImportRowProcessor()).importerLigne(eq(1L), org.mockito.ArgumentMatchers.argThat(l -> l.immatriculation().equals("CI-1234-AA")));
        doNothing().when(fx.vehiculeImportRowProcessor()).importerLigne(eq(1L), org.mockito.ArgumentMatchers.argThat(l -> l.immatriculation().equals("CI-5678-BB")));

        FleetImportResponse response = fx.service().confirm(1L, 10L);

        assertEquals(FleetImportStatus.PARTIALLY_COMPLETED, response.status());
        assertEquals(1, response.successCount());
        assertEquals(1, response.errorCount());
        verify(fx.fleetImportErrorRepository()).saveAll(any());
    }

    /** Regression : une ligne peut echouer sur une exception technique brute (pas seulement une
     * ResponseStatusException) - ex. DataIntegrityViolationException lors de la creation du vehicule,
     * bug reel constate en test E2E. Une seule ligne fautive ne doit jamais faire planter tout
     * l'import ni le laisser bloque a PROCESSING. */
    @Test
    void confirm_uneLigneLeveUneExceptionTechniqueBrute_estIsoleeSansPlanterToutLimport() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        String contenu = "immatriculation,type,marque,modele\nCI-1234-AA,AUTO,Toyota,Corolla\nCI-5678-BB,MOTO,Yamaha,MT07\n";
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));
        when(fx.fleetService().resolveFleet(1L)).thenReturn(imp.getFleet());
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.vehiculeImportStorageService().read("/uploads/fleet-imports/1/uuid.csv")).thenReturn(contenu.getBytes(StandardCharsets.UTF_8));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"))
                .when(fx.vehiculeImportRowProcessor()).importerLigne(eq(1L), org.mockito.ArgumentMatchers.argThat(l -> l.immatriculation().equals("CI-1234-AA")));
        doNothing().when(fx.vehiculeImportRowProcessor()).importerLigne(eq(1L), org.mockito.ArgumentMatchers.argThat(l -> l.immatriculation().equals("CI-5678-BB")));

        FleetImportResponse response = fx.service().confirm(1L, 10L);

        assertEquals(FleetImportStatus.PARTIALLY_COMPLETED, response.status());
        assertEquals(1, response.successCount());
        assertEquals(1, response.errorCount());
    }

    /** Regression : une erreur imprevue AVANT/APRES la boucle ligne par ligne (ex. echec de relecture
     * du fichier stocke) ne doit jamais laisser l'import bloque a PROCESSING sans issue. */
    @Test
    void confirm_erreurImprevueHorsBoucle_passeAFailedAuLieuDeResterBloqueAProcessing() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));
        when(fx.fleetService().resolveFleet(1L)).thenReturn(imp.getFleet());
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.vehiculeImportStorageService().read("/uploads/fleet-imports/1/uuid.csv"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier d'import introuvable"));

        assertThrows(ResponseStatusException.class, () -> fx.service().confirm(1L, 10L));

        ArgumentCaptor<FleetImport> captor = ArgumentCaptor.forClass(FleetImport.class);
        verify(fx.fleetImportRepository(), org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertEquals(FleetImportStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void confirm_toutesLesLignesEchouent_statutFailed() {
        Fixture fx = newFixture();
        FleetImport imp = importReady(1L);
        String contenu = "immatriculation,type,marque,modele\nCI-1234-AA,AUTO,Toyota,Corolla\n";
        imp.setTotalRows(1);
        imp.setValidRows(1);
        when(fx.fleetImportRepository().findById(10L)).thenReturn(Optional.of(imp));
        when(fx.fleetService().resolveFleet(1L)).thenReturn(imp.getFleet());
        when(fx.fleetImportRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.vehiculeImportStorageService().read("/uploads/fleet-imports/1/uuid.csv")).thenReturn(contenu.getBytes(StandardCharsets.UTF_8));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule non eligible"))
                .when(fx.vehiculeImportRowProcessor()).importerLigne(anyLong(), any());

        FleetImportResponse response = fx.service().confirm(1L, 10L);

        assertEquals(FleetImportStatus.FAILED, response.status());
        assertEquals(0, response.successCount());
        assertEquals(1, response.errorCount());
    }
}
