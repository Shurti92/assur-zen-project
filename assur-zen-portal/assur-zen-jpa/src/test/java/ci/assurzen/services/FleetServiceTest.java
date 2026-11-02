package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AddVehicleRequest;
import ci.assurzen.dto.FleetContractResponse;
import ci.assurzen.dto.FleetMovementResponse;
import ci.assurzen.dto.FleetRequest;
import ci.assurzen.dto.FleetResponse;
import ci.assurzen.dto.FleetVehicleResponse;
import ci.assurzen.dto.RemoveVehicleRequest;
import ci.assurzen.dto.ReplaceVehicleRequest;
import ci.assurzen.dto.TransferVehicleRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.FleetMovement;
import ci.assurzen.entities.FleetMovementType;
import ci.assurzen.entities.FleetStatus;
import ci.assurzen.entities.FleetType;
import ci.assurzen.entities.FleetVehicle;
import ci.assurzen.entities.FleetVehicleStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.User;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.FleetMovementRepository;
import ci.assurzen.repositories.FleetRepository;
import ci.assurzen.repositories.FleetVehicleRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VehiculeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le noyau metier du module Gestion de flotte (Phase 1) : machine a etats de la flotte,
 * regles d'ajout/retrait/remplacement/transfert de vehicules, et deux garde-fous de regression
 * directement lies a des bugs deja rencontres dans ce projet : recalcul des compteurs par requete
 * COUNT (jamais par increment en memoire) et systematisation de l'audit fonctionnel sur chaque
 * mouvement.
 */
class FleetServiceTest {

    private record Fixture(
            FleetService service,
            FleetRepository fleetRepository,
            FleetVehicleRepository fleetVehicleRepository,
            FleetMovementRepository fleetMovementRepository,
            VehiculeRepository vehiculeRepository,
            InsuredRepository insuredRepository,
            ProduitRepository produitRepository,
            ContractRepository contractRepository,
            UserRepository userRepository,
            ProfileRepository profileRepository,
            CodeGeneratorService codeGeneratorService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder,
            SmsNotificationService smsNotificationService
    ) {
    }

    private Fixture newFixture() {
        FleetRepository fleetRepository = mock(FleetRepository.class);
        FleetVehicleRepository fleetVehicleRepository = mock(FleetVehicleRepository.class);
        FleetMovementRepository fleetMovementRepository = mock(FleetMovementRepository.class);
        VehiculeRepository vehiculeRepository = mock(VehiculeRepository.class);
        InsuredRepository insuredRepository = mock(InsuredRepository.class);
        ProduitRepository produitRepository = mock(ProduitRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);

        FleetService service = new FleetService(fleetRepository, fleetVehicleRepository, fleetMovementRepository,
                vehiculeRepository, insuredRepository, produitRepository, contractRepository, userRepository, profileRepository,
                codeGeneratorService, authService, functionalAuditRecorder, smsNotificationService);

        return new Fixture(service, fleetRepository, fleetVehicleRepository, fleetMovementRepository, vehiculeRepository,
                insuredRepository, produitRepository, contractRepository, userRepository, profileRepository, codeGeneratorService,
                authService, functionalAuditRecorder, smsNotificationService);
    }

    /** FleetService.resolveVehicule n'utilise plus de verrou pessimiste (incompatible avec MariaDB,
     * cf. le commentaire de cette methode) - simple findById a stubber. */
    private void stubVehicule(VehiculeRepository vehiculeRepository, Vehicule vehicule) {
        when(vehiculeRepository.findById(vehicule.getId())).thenReturn(Optional.of(vehicule));
    }

    private Insured insured(long id) {
        return Insured.builder().id(id).nom("Client").prenoms("Test").build();
    }

    private Fleet fleet(long id, FleetStatus status, Insured client) {
        return Fleet.builder().id(id).fleetNumber("FLT2026" + String.format("%06d", id)).client(client)
                .name("Flotte test").fleetType(FleetType.AUTO).status(status).totalVehicles(0).activeVehicles(0).build();
    }

    private Vehicule vehicule(long id, VehiculeStatut statut, Insured proprietaire, Fleet currentFleet) {
        return Vehicule.builder().id(id).immatriculation("AB" + id + "CD").type(VehiculeType.AUTO)
                .marque("Toyota").modele("Corolla").proprietaire(proprietaire).statut(statut).currentFleet(currentFleet).build();
    }

    private void stubCurrentUser(AuthService authService, UserRepository userRepository) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(99L, "admin", "ADMIN");
        when(authService.currentPrincipal()).thenReturn(principal);
        User user = new User();
        user.setId(99L);
        user.setUsername("admin");
        when(userRepository.findById(99L)).thenReturn(Optional.of(user));
    }

    // ---------------------------------------------------------------- Flotte : creation + machine a etats

    @Test
    void creerFlotte_genereUnNumeroAutomatiquementSiAbsent() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));
        when(fx.fleetRepository().findByFleetNumber(any())).thenReturn(Optional.empty());
        when(fx.codeGeneratorService().generate(CodePrefix.FLOTTE)).thenReturn("FLT2026000042");
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetRequest request = new FleetRequest(null, 1L, "Flotte Commerciale", null, FleetType.AUTO, null, null, null, null, null, null);
        FleetResponse response = fx.service().create(request);

        assertEquals("FLT2026000042", response.fleetNumber());
        verify(fx.codeGeneratorService()).generate(CodePrefix.FLOTTE);
    }

    @Test
    void creerFlotte_statutInitialEstDraft() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));
        when(fx.fleetRepository().findByFleetNumber("FLT2026000001")).thenReturn(Optional.empty());
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetRequest request = new FleetRequest("FLT2026000001", 1L, "Flotte", null, FleetType.AUTO, null, null, null, null, null, null);
        FleetResponse response = fx.service().create(request);

        assertEquals(FleetStatus.DRAFT, response.status());
    }

    @Test
    void creerFlotte_avecProduit_lieLeProduitEtLExposeDansLaReponse() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));
        when(fx.fleetRepository().findByFleetNumber("FLT2026000001")).thenReturn(Optional.empty());
        when(fx.produitRepository().findById(9L)).thenReturn(Optional.of(ci.assurzen.entities.Produit.builder().id(9L).nomProduit("Flotte Entreprise").build()));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetRequest request = new FleetRequest("FLT2026000001", 1L, "Flotte", null, FleetType.AUTO, 9L, null, null, null, null, null);
        FleetResponse response = fx.service().create(request);

        assertEquals(9L, response.produitId());
        assertEquals("Flotte Entreprise", response.produitNom());
    }

    @Test
    void creerFlotte_produitInexistant_renvoie404() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));
        when(fx.fleetRepository().findByFleetNumber("FLT2026000001")).thenReturn(Optional.empty());
        when(fx.produitRepository().findById(9L)).thenReturn(Optional.empty());

        FleetRequest request = new FleetRequest("FLT2026000001", 1L, "Flotte", null, FleetType.AUTO, 9L, null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void creerFlotte_sansProduit_produitIdEstNull() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().findById(1L)).thenReturn(Optional.of(insured(1L)));
        when(fx.fleetRepository().findByFleetNumber("FLT2026000001")).thenReturn(Optional.empty());
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetRequest request = new FleetRequest("FLT2026000001", 1L, "Flotte", null, FleetType.AUTO, null, null, null, null, null, null);
        FleetResponse response = fx.service().create(request);

        assertEquals(null, response.produitId());
        org.mockito.Mockito.verifyNoInteractions(fx.produitRepository());
    }

    @Test
    void activerFlotte_depuisDraft_passeAActive() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.DRAFT, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetResponse response = fx.service().activate(1L);

        assertEquals(FleetStatus.ACTIVE, response.status());
    }

    @Test
    void activerFlotte_depuisSuspended_passeAActive() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.SUSPENDED, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetResponse response = fx.service().activate(1L);

        assertEquals(FleetStatus.ACTIVE, response.status());
    }

    @Test
    void activerFlotte_dejaActive_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().activate(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void suspendreFlotte_depuisActive_passeASuspended() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetResponse response = fx.service().suspend(1L);

        assertEquals(FleetStatus.SUSPENDED, response.status());
    }

    @Test
    void suspendreFlotte_depuisDraft_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.DRAFT, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().suspend(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void clorerFlotte_depuisActive_passeAClosed() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetResponse response = fx.service().close(1L);

        assertEquals(FleetStatus.CLOSED, response.status());
    }

    @Test
    void clorerFlotte_dejaCloturee_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.CLOSED, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().close(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void supprimerFlotte_depuisDraft_estUneSuppressionLogique() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.DRAFT, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        fx.service().delete(1L);

        assertEquals(true, fleet.isDeleted());
        verify(fx.fleetRepository()).save(fleet);
    }

    @Test
    void supprimerFlotte_dejaActivee_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void modifierFlotte_cloturee_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.CLOSED, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        FleetRequest request = new FleetRequest("FLT2026000001", 1L, "Nouveau nom", null, FleetType.AUTO, null, null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(1L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- Ajout de vehicule

    @Test
    void ajouterVehicule_flotteInexistante_renvoie404() {
        Fixture fx = newFixture();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_flotteNonActive_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.DRAFT, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_vehiculeHorsService_estRejeteAvec409() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.HORS_SERVICE, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_dejaDansUneAutreFlotte_estRejete() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet autreFlotte = fleet(2L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, autreFlotte);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_proprietaireDifferentDuClientDeLaFlotte_estAccepte() {
        // Regle metier du 2026-09-24 : un vehicule peut rejoindre une flotte meme si son
        // proprietaire n'est pas le client de la flotte (auparavant rejete en 409).
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, insured(2L), null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetVehicleRepository().countDistinctVehiculeIdByFleetId(1L)).thenReturn(1L);
        when(fx.fleetVehicleRepository().countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE)).thenReturn(1L);
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        assertEquals(FleetVehicleStatus.ACTIVE, response.status());
    }

    @Test
    void ajouterVehicule_motoDansFlotteAuto_estRejeteAvec409() {
        // Regle de coherence type flotte / type vehicule (2026-09-13) - avant ce correctif, rien
        // n'empechait d'incorporer une moto dans une flotte AUTO (ou l'inverse).
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleetAuto = Fleet.builder().id(1L).fleetNumber("FLT2026000001").client(client)
                .name("Flotte auto").fleetType(FleetType.AUTO).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        Vehicule moto = Vehicule.builder().id(10L).immatriculation("AB10CD").type(VehiculeType.MOTO)
                .marque("Yamaha").modele("Crypton").proprietaire(client).statut(VehiculeStatut.ACTIF).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleetAuto));
        stubVehicule(fx.vehiculeRepository(), moto);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_autoDansFlotteMoto_estRejeteAvec409() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleetMoto = Fleet.builder().id(1L).fleetNumber("FLT2026000001").client(client)
                .name("Flotte moto").fleetType(FleetType.MOTO).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        Vehicule auto = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleetMoto));
        stubVehicule(fx.vehiculeRepository(), auto);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void ajouterVehicule_motoDansFlotteMixte_estAccepte() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleetMixte = Fleet.builder().id(1L).fleetNumber("FLT2026000001").client(client)
                .name("Flotte mixte").fleetType(FleetType.MIXED).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        Vehicule moto = Vehicule.builder().id(10L).immatriculation("AB10CD").type(VehiculeType.MOTO)
                .marque("Yamaha").modele("Crypton").proprietaire(client).statut(VehiculeStatut.ACTIF).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleetMixte));
        stubVehicule(fx.vehiculeRepository(), moto);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetVehicleRepository().countDistinctVehiculeIdByFleetId(1L)).thenReturn(1L);
        when(fx.fleetVehicleRepository().countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE)).thenReturn(1L);
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        assertEquals(FleetVehicleStatus.ACTIVE, response.status());
    }

    @Test
    void ajouterVehicule_casNominal_creeFleetVehicleActifEtIncrementeLesCompteurs() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetVehicleRepository().countDistinctVehiculeIdByFleetId(1L)).thenReturn(1L);
        when(fx.fleetVehicleRepository().countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE)).thenReturn(1L);
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        assertEquals(FleetVehicleStatus.ACTIVE, response.status());
        assertEquals(10L, response.vehiculeId());

        ArgumentCaptor<Fleet> captor = ArgumentCaptor.forClass(Fleet.class);
        verify(fx.fleetRepository()).save(captor.capture());
        assertEquals(1, captor.getValue().getTotalVehicles());
        assertEquals(1, captor.getValue().getActiveVehicles());
    }

    @Test
    void ajouterVehicule_casNominal_ecritUnMouvementAdd() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif de test"));

        ArgumentCaptor<FleetMovement> captor = ArgumentCaptor.forClass(FleetMovement.class);
        verify(fx.fleetMovementRepository()).save(captor.capture());
        assertEquals(FleetMovementType.ADD, captor.getValue().getMovementType());
        assertEquals("motif de test", captor.getValue().getReason());
    }

    // ---------------------------------------------------------------- SMS d'incorporation (§2026-09-08)

    @Test
    void ajouterVehicule_casNominal_envoieUnSmsAuGestionnaireDeLaFlotte() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        fleet.setManagerPhone("0700000001");
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000001"), eq("VEHICULE_FLOTTE_DETAIL"), any(), any()))
                .thenReturn(true);

        FleetVehicleResponse response = fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("VEHICULE_FLOTTE_DETAIL"),
                eq(java.util.Map.of("typeVehicule", "vehicule", "marque", "Toyota", "modele", "Corolla",
                        "flotteNom", fleet.getName(), "flotteNumero", fleet.getFleetNumber(), "policeSuffix", "")),
                eq(ci.assurzen.entities.TypeMessageSms.vehicule_incorpore_flotte));
        assertEquals(Boolean.TRUE, response.notificationEnvoyee());
    }

    @Test
    void ajouterVehicule_motoAvecContratExistant_smsDetailleInclutTypeEtNumeroDePolice() {
        // Le type devient "moto" (pas "vehicule") et le n de police du contrat le plus recent deja lie
        // a ce vehicule apparait dans le SMS - aucun contrat n'est cree a l'incorporation, simple lecture.
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = Fleet.builder().id(1L).fleetNumber("FLT2026000001").client(client)
                .name("Flotte test").fleetType(FleetType.MOTO).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        fleet.setManagerPhone("0700000001");
        Vehicule moto = Vehicule.builder().id(10L).immatriculation("AB10CD").type(VehiculeType.MOTO)
                .marque("Yamaha").modele("Crypton").proprietaire(client).statut(VehiculeStatut.ACTIF).build();
        Contract contratExistant = Contract.builder().id(99L).policyNumber("CTR2026000099").build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), moto);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of(contratExistant));
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000001"), eq("VEHICULE_FLOTTE_DETAIL"), any(), any()))
                .thenReturn(true);

        fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("VEHICULE_FLOTTE_DETAIL"),
                eq(java.util.Map.of("typeVehicule", "moto", "marque", "Yamaha", "modele", "Crypton",
                        "flotteNom", fleet.getName(), "flotteNumero", fleet.getFleetNumber(), "policeSuffix", " - police CTR2026000099")),
                eq(ci.assurzen.entities.TypeMessageSms.vehicule_incorpore_flotte));
    }

    @Test
    void ajouterVehicule_sansTelephoneGestionnaire_retombeSurLeTelephoneDuClient() {
        Fixture fx = newFixture();
        Insured client = Insured.builder().id(1L).nom("Client").prenoms("Test").clientPhone("0700000002").build();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client); // managerPhone absent
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000002"), eq("VEHICULE_FLOTTE_DETAIL"), any(), any());
    }

    @Test
    void ajouterVehicule_aucunTelephoneDisponible_neCrashePasEtNenvoieRien() {
        Fixture fx = newFixture();
        Insured client = insured(1L); // pas de clientPhone
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client); // pas de managerPhone
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.smsNotificationService(), org.mockito.Mockito.never())
                .sendFromTemplate(any(), any(), any(), any());
        assertNull(response.notificationEnvoyee());
    }

    @Test
    void notifierIncorporation_zeroVehicule_nenvoieRien() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        fleet.setManagerPhone("0700000001");

        fx.service().notifierIncorporation(fleet, 0);

        verify(fx.smsNotificationService(), org.mockito.Mockito.never())
                .sendFromTemplate(any(), any(), any(), any());
    }

    @Test
    void notifierIncorporationApresImport_rechargeLaFlotteAvantDenvoyerLeSms() {
        // Ce point d'entree existe specifiquement pour VehiculeImportService.confirm() (bug reel du
        // 2026-09-13, LazyInitializationException sur fleet.getClient() car confirm() n'est pas
        // transactionnel) - verifie que le Fleet est bien rechargeee via resolveFleet(fleetId), pas
        // reutilise tel quel.
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        fleet.setManagerPhone("0700000001");
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000001"), eq("VEHICULE_INCORPORE_FLOTTE"), any(), any()))
                .thenReturn(true);

        Boolean result = fx.service().notifierIncorporationApresImport(1L, 3);

        assertEquals(Boolean.TRUE, result);
        verify(fx.fleetRepository()).findById(1L);
        verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000001"), eq("VEHICULE_INCORPORE_FLOTTE"),
                eq(java.util.Map.of("nombreVehicules", "3", "flotteNom", fleet.getName(), "flotteNumero", fleet.getFleetNumber())),
                eq(ci.assurzen.entities.TypeMessageSms.vehicule_incorpore_flotte));
    }

    // ---------------------------------------------------------------- Retrait de vehicule

    @Test
    void retirerVehicule_nonPresentActifDansLaFlotte_renvoie404() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().removeVehicle(1L, new RemoveVehicleRequest(10L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void retirerVehicule_casNominal_passeLeStatutARemovedEtLibereLeVehicule() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        FleetVehicle row = FleetVehicle.builder().id(50L).fleet(fleet).vehicule(vehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(row));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().removeVehicle(1L, new RemoveVehicleRequest(10L, "motif"));

        assertEquals(FleetVehicleStatus.REMOVED, response.status());
        assertNull(vehicule.getCurrentFleet());
    }

    @Test
    void retirerVehicule_flotteSuspendue_estAutorise() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.SUSPENDED, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        FleetVehicle row = FleetVehicle.builder().id(50L).fleet(fleet).vehicule(vehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(row));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().removeVehicle(1L, new RemoveVehicleRequest(10L, null));

        assertEquals(FleetVehicleStatus.REMOVED, response.status());
    }

    // ---------------------------------------------------------------- Remplacement de vehicule

    @Test
    void remplacerVehicule_nouveauVehiculeInexistant_renvoie404() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule oldVehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        FleetVehicle oldRow = FleetVehicle.builder().id(50L).fleet(fleet).vehicule(oldVehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(oldRow));
        stubVehicule(fx.vehiculeRepository(), oldVehicule);
        when(fx.vehiculeRepository().findById(20L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().replaceVehicle(1L, new ReplaceVehicleRequest(10L, 20L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void remplacerVehicule_ancienVehiculeNonActifDansLaFlotte_renvoie404() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().replaceVehicle(1L, new ReplaceVehicleRequest(10L, 20L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void remplacerVehicule_parLuiMeme_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().replaceVehicle(1L, new ReplaceVehicleRequest(10L, 10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void remplacerVehicule_nouveauVehiculeDejaDansUneAutreFlotte_estRejete() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet autreFlotte = fleet(2L, FleetStatus.ACTIVE, client);
        Vehicule oldVehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        Vehicule newVehicule = vehicule(20L, VehiculeStatut.ACTIF, client, autreFlotte);
        FleetVehicle oldRow = FleetVehicle.builder().id(50L).fleet(fleet).vehicule(oldVehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(oldRow));
        stubVehicule(fx.vehiculeRepository(), oldVehicule);
        stubVehicule(fx.vehiculeRepository(), newVehicule);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().replaceVehicle(1L, new ReplaceVehicleRequest(10L, 20L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void remplacerVehicule_casNominal_clotureLancienEtActiveLeNouveau() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule oldVehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        Vehicule newVehicule = vehicule(20L, VehiculeStatut.ACTIF, client, null);
        FleetVehicle oldRow = FleetVehicle.builder().id(50L).fleet(fleet).vehicule(oldVehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(oldRow));
        stubVehicule(fx.vehiculeRepository(), oldVehicule);
        stubVehicule(fx.vehiculeRepository(), newVehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().replaceVehicle(1L, new ReplaceVehicleRequest(10L, 20L, "motif"));

        assertEquals(FleetVehicleStatus.REPLACED, oldRow.getStatus());
        assertEquals(FleetVehicleStatus.ACTIVE, response.status());
        assertEquals(20L, response.vehiculeId());
        assertNull(oldVehicule.getCurrentFleet());
        assertEquals(fleet, newVehicule.getCurrentFleet());
    }

    // ---------------------------------------------------------------- Transfert de vehicule

    @Test
    void transfererVehicule_versLaMemeFlotte_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(1L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void transfererVehicule_versUneFlotteDeClientDifferent_estRejeteAvec409() {
        Fixture fx = newFixture();
        Fleet source = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        Fleet destination = fleet(2L, FleetStatus.ACTIVE, insured(2L));
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(source));
        when(fx.fleetRepository().findById(2L)).thenReturn(Optional.of(destination));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(2L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void transfererVehicule_versFlotteNonActive_estRejeteAvec409() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet source = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet destination = fleet(2L, FleetStatus.SUSPENDED, client);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(source));
        when(fx.fleetRepository().findById(2L)).thenReturn(Optional.of(destination));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(2L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void transfererVehicule_nonPresentActifDansLaFlotteSource_renvoie404() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet source = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet destination = fleet(2L, FleetStatus.ACTIVE, client);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(source));
        when(fx.fleetRepository().findById(2L)).thenReturn(Optional.of(destination));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(2L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void transfererVehicule_casNominal_deplaceLeVehiculeEtRecalculeLesDeuxCompteurs() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet source = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet destination = fleet(2L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, source);
        FleetVehicle sourceRow = FleetVehicle.builder().id(50L).fleet(source).vehicule(vehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(source));
        when(fx.fleetRepository().findById(2L)).thenReturn(Optional.of(destination));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(sourceRow));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetVehicleResponse response = fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(2L, "motif"));

        assertEquals(FleetVehicleStatus.TRANSFERRED, sourceRow.getStatus());
        assertEquals(FleetVehicleStatus.ACTIVE, response.status());
        assertEquals(destination, vehicule.getCurrentFleet());
        verify(fx.fleetVehicleRepository()).countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE);
        verify(fx.fleetVehicleRepository()).countByFleetIdAndStatus(2L, FleetVehicleStatus.ACTIVE);
    }

    @Test
    void transfererVehicule_motoVersFlotteAuto_estRejeteAvec409() {
        // Regression (2026-09-13, meme jour) : transferVehicle() a sa propre logique inline et
        // n'appelait pas requireVehiculeEligible() - la regle de coherence type flotte/vehicule
        // ajoutee sur addVehicle/replaceVehicle le jour meme avait ete oubliee ici, constate en
        // direct (une moto se transferait sans blocage vers une flotte AUTO).
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet source = Fleet.builder().id(1L).fleetNumber("FLT2026000001").client(client)
                .name("Flotte moto").fleetType(FleetType.MOTO).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        Fleet destinationAuto = Fleet.builder().id(2L).fleetNumber("FLT2026000002").client(client)
                .name("Flotte auto").fleetType(FleetType.AUTO).status(FleetStatus.ACTIVE).totalVehicles(0).activeVehicles(0).build();
        Vehicule moto = Vehicule.builder().id(10L).immatriculation("AB10CD").type(VehiculeType.MOTO)
                .marque("Yamaha").modele("Crypton").proprietaire(client).statut(VehiculeStatut.ACTIF).currentFleet(source).build();
        FleetVehicle sourceRow = FleetVehicle.builder().id(50L).fleet(source).vehicule(moto)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(source));
        when(fx.fleetRepository().findById(2L)).thenReturn(Optional.of(destinationAuto));
        when(fx.fleetVehicleRepository().findByFleetIdAndVehiculeIdAndStatus(1L, 10L, FleetVehicleStatus.ACTIVE)).thenReturn(Optional.of(sourceRow));
        stubVehicule(fx.vehiculeRepository(), moto);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().transferVehicle(1L, 10L, new TransferVehicleRequest(2L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.fleetVehicleRepository(), org.mockito.Mockito.never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- Garde-fous de regression

    @Test
    void recalculDesCompteurs_utiliseUneRequeteCountPasUnIncrementEnMemoire() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleetEntity = fleet(1L, FleetStatus.ACTIVE, client);
        fleetEntity.setActiveVehicles(0);
        fleetEntity.setTotalVehicles(0);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleetEntity));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        // Delibrement != 0 + 1 : si le code incrementait en memoire, le resultat attendu serait 1, pas 5.
        when(fx.fleetVehicleRepository().countDistinctVehiculeIdByFleetId(1L)).thenReturn(5L);
        when(fx.fleetVehicleRepository().countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE)).thenReturn(5L);
        stubCurrentUser(fx.authService(), fx.userRepository());

        fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.fleetVehicleRepository()).countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE);
        verify(fx.fleetVehicleRepository()).countDistinctVehiculeIdByFleetId(1L);
        assertEquals(5, fleetEntity.getActiveVehicles());
        assertEquals(5, fleetEntity.getTotalVehicles());
    }

    @Test
    void chaqueMouvement_declencheUnAppelAFunctionalAuditRecorder() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        fx.service().addVehicle(1L, new AddVehicleRequest(10L, "motif"));

        verify(fx.functionalAuditRecorder()).recordAction(eq("FLOTTE"), eq("Vehicule"), eq("10"), eq(ActionAudit.ADD), any());
    }

    // ---------------------------------------------------------------- Regression : @NotFound(IGNORE)
    // peut renvoyer fv.getVehicule() == null (2026-09-13, meme jour) - trouve en direct par le
    // frontend sur GET /fleets/{id}/contracts (NullPointerException, pas ObjectNotFoundException
    // cette fois, sur .getVehicule().getId() sans garde-fou). Meme risque verifie/corrige sur
    // listVehicles() (filtre par type) et listEcheances() (defense en profondeur, non atteignable
    // en theorie vu que VehiculeService.delete() bloque un vehicule encore ACTIVE dans une flotte).

    @Test
    void listerContrats_ligneHistoriqueAvecVehiculeSupprime_estIgnoreeSansPlanter() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        FleetVehicle ligneOrpheline = FleetVehicle.builder().id(99L).fleet(fleet).vehicule(null)
                .status(FleetVehicleStatus.REPLACED).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdOrderByCreatedAtDesc(1L)).thenReturn(java.util.List.of(ligneOrpheline));

        assertEquals(java.util.List.of(), fx.service().listContracts(1L));
        org.mockito.Mockito.verifyNoInteractions(fx.contractRepository());
    }

    @Test
    void listerVehicules_filtreParTypeAvecLigneVehiculeSupprime_neplantePas() {
        Fixture fx = newFixture();
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, insured(1L));
        FleetVehicle ligneOrpheline = FleetVehicle.builder().id(99L).fleet(fleet).vehicule(null)
                .status(FleetVehicleStatus.REPLACED).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.fleetVehicleRepository().findByFleetIdOrderByCreatedAtDesc(1L)).thenReturn(java.util.List.of(ligneOrpheline));

        var response = fx.service().listVehicles(1L, null, VehiculeType.AUTO, 0, 20);

        assertEquals(0, response.content().size());
    }

    // ---------------------------------------------------------------- listEcheances (2026-09-13)

    @Test
    void listerEcheances_aucunVehiculeActifDansLaFlotte_renvoieUneListeVide() {
        Fixture fx = newFixture();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, insured(1L))));
        when(fx.fleetVehicleRepository().findByFleetIdAndStatusOrderByCreatedAtDesc(1L, FleetVehicleStatus.ACTIVE)).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), fx.service().listEcheances(1L));
        org.mockito.Mockito.verifyNoInteractions(fx.contractRepository());
    }

    @Test
    void listerEcheances_vehiculeActifSansAucunContrat_nApparaitPas() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        FleetVehicle row = FleetVehicle.builder().id(1L).fleet(fleet(1L, FleetStatus.ACTIVE, client)).vehicule(vehicule)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.fleetVehicleRepository().findByFleetIdAndStatusOrderByCreatedAtDesc(1L, FleetVehicleStatus.ACTIVE)).thenReturn(java.util.List.of(row));
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), fx.service().listEcheances(1L));
    }

    @Test
    void listerEcheances_vehiculeActifAvecContrat_renvoieLeContratEtSesRappelsRegroupes() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Vehicule moto = Vehicule.builder().id(10L).immatriculation("AB10CD").type(VehiculeType.MOTO)
                .marque("Yamaha").modele("Crypton").proprietaire(client).statut(VehiculeStatut.ACTIF).build();
        FleetVehicle row = FleetVehicle.builder().id(1L).fleet(fleet(1L, FleetStatus.ACTIVE, client)).vehicule(moto)
                .status(FleetVehicleStatus.ACTIVE).dateAdded(LocalDate.now()).build();
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").vehicule(moto)
                .dateEffet(LocalDate.of(2026, 9, 13)).dateEcheance(LocalDate.of(2027, 9, 13)).build();
        ci.assurzen.entities.NotificateurSms rappel = ci.assurzen.entities.NotificateurSms.builder()
                .id(5L).contract(contrat).telephoneAssure("0799999997").message("...")
                .typeMessage(ci.assurzen.entities.TypeMessageSms.avis_echeance)
                .dateEnvoi(LocalDate.of(2027, 7, 30)).statutEnvoi(ci.assurzen.entities.StatutEnvoiSms.en_attente).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.fleetVehicleRepository().findByFleetIdAndStatusOrderByCreatedAtDesc(1L, FleetVehicleStatus.ACTIVE)).thenReturn(java.util.List.of(row));
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of(contrat));
        when(fx.smsNotificationService().getMessagesForContracts(java.util.List.of(99L)))
                .thenReturn(java.util.List.of(new ci.assurzen.dto.NotificateurSmsResponse(5L, 99L, "0799999997", "...",
                        ci.assurzen.entities.TypeMessageSms.avis_echeance, LocalDate.of(2027, 7, 30), ci.assurzen.entities.StatutEnvoiSms.en_attente, null)));

        java.util.List<ci.assurzen.dto.FleetEcheanceResponse> resultat = fx.service().listEcheances(1L);

        assertEquals(1, resultat.size());
        ci.assurzen.dto.FleetEcheanceResponse echeance = resultat.get(0);
        assertEquals("CTR2026000099", echeance.policyNumber());
        assertEquals(LocalDate.of(2027, 9, 13), echeance.dateEcheance());
        assertEquals(VehiculeType.MOTO, echeance.vehiculeType());
        assertEquals(1, echeance.rappels().size());
        assertEquals(ci.assurzen.entities.TypeMessageSms.avis_echeance, echeance.rappels().get(0).typeMessage());
    }

    // ---------------------------------------------------------------- Rattachement d'un contrat mono existant (§2026-09-14)

    @Test
    void attacherContrat_contratIntrouvable_renvoie404() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void attacherContrat_contratDejaRattacheAUnVehicule_estRejeteAvec409() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Vehicule vehiculeDejaLie = vehicule(5L, VehiculeStatut.ACTIF, client, null);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).vehicule(vehiculeDejaLie).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void attacherContrat_proprietaireDuVehiculeDifferentDeLAssureDuContrat_estRejete() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Insured autreClient = insured(2L);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, autreClient, null);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void attacherContrat_vehiculeDejaRattacheAUnAutreContratActif_estRejete() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet(1L, FleetStatus.ACTIVE, client));
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        Contract autreContrat = Contract.builder().id(50L).policyNumber("CTR2026000050").status(ContractStatus.signed).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet(1L, FleetStatus.ACTIVE, client)));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of(autreContrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void attacherContrat_vehiculeDejaRattacheAUnContratAnnule_estAccepte() {
        // Un contrat annule ne bloque pas le rattachement : seul un contrat non annule compte comme conflit.
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        Contract contratAnnule = Contract.builder().id(50L).policyNumber("CTR2026000050").status(ContractStatus.cancelled).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of(contratAnnule));
        when(fx.contractRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetContractResponse response = fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif"));

        assertEquals(10L, response.vehiculeId());
    }

    @Test
    void attacherContrat_vehiculeDejaMembreDeCetteFlotte_lieDirectementSansToucherAuxCompteurs() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, fleet);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of());
        when(fx.contractRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));

        FleetContractResponse response = fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif"));

        assertEquals(10L, response.vehiculeId());
        assertEquals("CTR2026000099", response.policyNumber());
        org.mockito.Mockito.verify(fx.fleetVehicleRepository(), org.mockito.Mockito.never()).saveAndFlush(any());
        org.mockito.Mockito.verify(fx.fleetRepository(), org.mockito.Mockito.never()).save(any());
    }

    @Test
    void attacherContrat_vehiculeDejaActifDansUneAutreFlotte_estRejete() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Fleet autreFlotte = fleet(2L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, autreFlotte);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void attacherContrat_vehiculeHorsDeTouteFlotte_yEstAjouteEtLieAuContratEnUneSeuleOperation() {
        Fixture fx = newFixture();
        Insured client = insured(1L);
        Fleet fleet = fleet(1L, FleetStatus.ACTIVE, client);
        Vehicule vehicule = vehicule(10L, VehiculeStatut.ACTIF, client, null);
        Contract contrat = Contract.builder().id(99L).policyNumber("CTR2026000099").insured(client).build();
        when(fx.fleetRepository().findById(1L)).thenReturn(Optional.of(fleet));
        when(fx.contractRepository().findById(99L)).thenReturn(Optional.of(contrat));
        stubVehicule(fx.vehiculeRepository(), vehicule);
        when(fx.contractRepository().findByVehiculeIdInOrderByCreatedAtDesc(java.util.List.of(10L))).thenReturn(java.util.List.of());
        when(fx.fleetVehicleRepository().saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetVehicleRepository().countDistinctVehiculeIdByFleetId(1L)).thenReturn(1L);
        when(fx.fleetVehicleRepository().countByFleetIdAndStatus(1L, FleetVehicleStatus.ACTIVE)).thenReturn(1L);
        when(fx.contractRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubCurrentUser(fx.authService(), fx.userRepository());

        FleetContractResponse response = fx.service().attachContract(1L, new ci.assurzen.dto.AttachContractRequest(99L, 10L, "motif d'ajout"));

        assertEquals(10L, response.vehiculeId());
        assertEquals(fleet, vehicule.getCurrentFleet());

        ArgumentCaptor<FleetMovement> movementCaptor = ArgumentCaptor.forClass(FleetMovement.class);
        verify(fx.fleetMovementRepository()).save(movementCaptor.capture());
        assertEquals(FleetMovementType.ADD, movementCaptor.getValue().getMovementType());

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(fx.contractRepository()).save(contractCaptor.capture());
        assertEquals(vehicule, contractCaptor.getValue().getVehicule());
    }
}
