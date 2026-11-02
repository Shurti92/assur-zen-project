package ci.assurzen.services;

import ci.assurzen.dto.DemandeVignetteMotoRequest;
import ci.assurzen.dto.DemandeVignetteMotoResponse;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.HistoriqueDemandeVignetteMotoResponse;
import ci.assurzen.dto.LienSuiviVignetteResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SimulationTarifVignetteMotoRequest;
import ci.assurzen.dto.SimulationTarifVignetteMotoResponse;
import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.FraisLivraisonZone;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TarifVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.GedDocumentTypeRepository;
import ci.assurzen.repositories.HistoriqueDemandeVignetteMotoRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VignetteCommissionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre §9 (LE point critique : un tarif modifie apres coup n'affecte jamais une demande deja
 * creee), §11 (creation, tarification figee), §13/§14 (documents obligatoires avant soumission,
 * distincts nouvelle demande / renouvellement) et les transitions de statut implementees
 * (BROUILLON -> EN_ATTENTE_PAIEMENT / ANNULEE / REJETEE).
 */
class DemandeVignetteMotoServiceTest {

    private static final LocalDate DATE_MISE_CIRCULATION = LocalDate.of(2020, 5, 15);

    private record Fixture(
            DemandeVignetteMotoService service,
            DemandeVignetteMotoRepository repository,
            TarifVignetteMotoService tarifVignetteMotoService,
            FraisLivraisonZoneService fraisLivraisonZoneService,
            GedDocumentService gedDocumentService,
            GedDocumentTypeRepository gedDocumentTypeRepository,
            CodeGeneratorService codeGeneratorService,
            AuthService authService,
            PartenaireRepository partenaireRepository,
            CommercialRepository commercialRepository,
            VignetteCommissionRepository vignetteCommissionRepository,
            DynamicConfigurationService dynamicConfigurationService,
            HistoriqueDemandeVignetteMotoRepository historiqueRepository,
            UserRepository userRepository,
            SmsNotificationService smsNotificationService,
            LienSuiviVignetteService lienSuiviVignetteService
    ) {
    }

    /** Principal ADMIN par defaut - la plupart des tests de ce fichier portent sur la tarification/le
     * workflow, pas sur le perimetre (§ garde-fou IDOR ajoute le 2026-09-12, couvert par sa propre
     * section de tests plus bas avec un principal non-admin explicite). */
    private Fixture newFixture() {
        return newFixture("service", "ADMIN");
    }

    private Fixture newFixture(String username, String roles) {
        DemandeVignetteMotoRepository repository = mock(DemandeVignetteMotoRepository.class);
        TarifVignetteMotoService tarifVignetteMotoService = mock(TarifVignetteMotoService.class);
        FraisLivraisonZoneService fraisLivraisonZoneService = mock(FraisLivraisonZoneService.class);
        GedDocumentService gedDocumentService = mock(GedDocumentService.class);
        GedDocumentTypeRepository gedDocumentTypeRepository = mock(GedDocumentTypeRepository.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
        CommercialRepository commercialRepository = mock(CommercialRepository.class);
        VignetteCommissionRepository vignetteCommissionRepository = mock(VignetteCommissionRepository.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        HistoriqueDemandeVignetteMotoRepository historiqueRepository = mock(HistoriqueDemandeVignetteMotoRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        LienSuiviVignetteService lienSuiviVignetteService = mock(LienSuiviVignetteService.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, username, roles));
        when(codeGeneratorService.generate(CodePrefix.VIGNETTE_MOTO)).thenReturn("VGN2026000001");
        User utilisateurCourant = new User();
        utilisateurCourant.setId(1L);
        utilisateurCourant.setUsername(username);
        when(userRepository.findById(1L)).thenReturn(Optional.of(utilisateurCourant));
        when(lienSuiviVignetteService.genererPourDemande(any())).thenReturn(
                new LienSuiviVignetteResponse(1L, 1L, "tokenBrutTest", "https://suivi.test/vignette?token=tokenBrutTest", Instant.now().plusSeconds(3600)));

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            DemandeVignetteMoto d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(idSeq.getAndIncrement());
            }
            return d;
        });

        // Tarif/zone par defaut : moto 2020-05-15 -> 6 ans a la date de reference "aujourd'hui" ->
        // AGE_5_10_ANS, cylindree 110 -> MOINS_125_CM3 (memes valeurs que l'exemple du contrat).
        when(tarifVignetteMotoService.resoudre(eq(CategorieAgeMoto.AGE_5_10_ANS), eq(CategorieCylindree.MOINS_125_CM3), any()))
                .thenReturn(tarif(3750, 1500));
        when(fraisLivraisonZoneService.resoudre("ABIDJAN")).thenReturn(zone("ABIDJAN", 1500));

        DemandeVignetteMotoService service = new DemandeVignetteMotoService(repository, tarifVignetteMotoService,
                fraisLivraisonZoneService, gedDocumentService, gedDocumentTypeRepository, codeGeneratorService, authService,
                partenaireRepository, commercialRepository, vignetteCommissionRepository, dynamicConfigurationService,
                historiqueRepository, userRepository, smsNotificationService, lienSuiviVignetteService);

        return new Fixture(service, repository, tarifVignetteMotoService, fraisLivraisonZoneService,
                gedDocumentService, gedDocumentTypeRepository, codeGeneratorService, authService,
                partenaireRepository, commercialRepository, vignetteCommissionRepository, dynamicConfigurationService,
                historiqueRepository, userRepository, smsNotificationService, lienSuiviVignetteService);
    }

    private TarifVignetteMoto tarif(int montantVignette, int fraisOperation) {
        return TarifVignetteMoto.builder().id(3L)
                .categorieAge(CategorieAgeMoto.AGE_5_10_ANS).categorieCylindree(CategorieCylindree.MOINS_125_CM3)
                .montantVignette(BigDecimal.valueOf(montantVignette)).fraisOperation(BigDecimal.valueOf(fraisOperation))
                .dateDebutValidite(LocalDate.of(2026, 1, 1)).actif(true).build();
    }

    private FraisLivraisonZone zone(String code, int montant) {
        return FraisLivraisonZone.builder().id(1L).code(code).libelle(code).montant(BigDecimal.valueOf(montant)).actif(true).build();
    }

    private DemandeVignetteMotoRequest requestNouvelleDemande() {
        return new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", 110, DATE_MISE_CIRCULATION, null, null);
    }

    private DemandeVignetteMotoRequest requestRenouvellement() {
        return new DemandeVignetteMotoRequest(TypeDemandeVignette.RENOUVELLEMENT,
                "DIALLO", "Awa", "0700000000", null, "Adjame", "Abidjan", "ABIDJAN",
                "Sanili", "SL110", "CH-002", 110, DATE_MISE_CIRCULATION, null, null);
    }

    private GedDocumentResponse document(String typeCode) {
        return new GedDocumentResponse(1L, "uuid", "GED001", 1L, typeCode, typeCode, null, null, null,
                "titre", null, null, null, null, null, null, 1, null, null, null, null, null, null, null);
    }

    private PageResponse<GedDocumentResponse> pageOf(GedDocumentResponse... documents) {
        return new PageResponse<>(List.of(documents), 0, 20, documents.length, 1);
    }

    // ---------------------------------------------------------------- simuler() (§10)

    @Test
    void simuler_correspondExactementALexempleDuContrat() {
        Fixture fx = newFixture();

        SimulationTarifVignetteMotoResponse response = fx.service()
                .simuler(new SimulationTarifVignetteMotoRequest(DATE_MISE_CIRCULATION, 110, "ABIDJAN", null, null));

        assertEquals(CategorieAgeMoto.AGE_5_10_ANS, response.categorieAge());
        assertEquals(CategorieCylindree.MOINS_125_CM3, response.categorieCylindree());
        assertEquals(0, BigDecimal.valueOf(3750).compareTo(response.montantVignette()));
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(response.fraisOperation()));
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(response.fraisLivraison()));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(response.fraisAccessoires()));
        assertEquals(0, BigDecimal.valueOf(6750).compareTo(response.montantTotal()));
        assertEquals(null, response.reductionApporteur());
    }

    @Test
    void simuler_avecPartenaireApporteurZoneAbidjan_refleteLaReductionDansLApercu() {
        // Contrat "Commission apporteur variable par zone", 2026-09-19 : l'apercu doit deja montrer
        // le prix reduit si l'utilisateur a choisi un apporteur avant de simuler.
        Fixture fx = newFixture();
        ci.assurzen.entities.Partenaire partenaire = ci.assurzen.entities.Partenaire.builder().id(5L)
                .nom("Yao").prenoms("Marie").telephone("0700000001").build();
        when(fx.partenaireRepository().findById(5L)).thenReturn(Optional.of(partenaire));
        when(fx.dynamicConfigurationService().getLong(eq("VIGNETTE_COMMISSION_APPORTEUR_MONTANT_ABIDJAN"), any()))
                .thenReturn(500L);

        SimulationTarifVignetteMotoResponse response = fx.service()
                .simuler(new SimulationTarifVignetteMotoRequest(DATE_MISE_CIRCULATION, 110, "ABIDJAN", 5L, null));

        assertEquals(0, BigDecimal.valueOf(500).compareTo(response.reductionApporteur()));
        assertEquals(0, BigDecimal.valueOf(6250).compareTo(response.montantTotal()));
    }

    @Test
    void simuler_apporteurInconnu_leve404_memeAvantCreationReelle() {
        Fixture fx = newFixture();
        when(fx.partenaireRepository().findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service()
                .simuler(new SimulationTarifVignetteMotoRequest(DATE_MISE_CIRCULATION, 110, "ABIDJAN", 99L, null)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void simuler_partenaireEtCommercialApporteursTousLesDeux_leve400() {
        Fixture fx = newFixture();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service()
                .simuler(new SimulationTarifVignetteMotoRequest(DATE_MISE_CIRCULATION, 110, "ABIDJAN", 5L, 9L)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void simuler_neSauvegardeRien() {
        Fixture fx = newFixture();
        fx.service().simuler(new SimulationTarifVignetteMotoRequest(DATE_MISE_CIRCULATION, 110, "ABIDJAN", null, null));
        org.mockito.Mockito.verifyNoInteractions(fx.repository());
    }

    // ---------------------------------------------------------------- creer() (§11) + LE test critique du §9

    @Test
    void creer_figeLesMontantsAuMomentDeLaCreation() {
        Fixture fx = newFixture();

        DemandeVignetteMotoResponse response = fx.service().creer(requestNouvelleDemande());

        assertEquals("VGN2026000001", response.reference());
        assertEquals(StatutDemandeVignetteMoto.BROUILLON, response.statut());
        assertEquals(0, BigDecimal.valueOf(6750).compareTo(response.montantTotal()));
    }

    @Test
    void creer_puisModificationDuTarifSourceApresCoup_neChangePasLeMontantDejaFigeSurLaReponse() {
        // C'est le point le plus important du contrat (§9) : "il ne faut PAS recalculer le montant
        // d'une ancienne demande a partir du tarif actuellement configure".
        Fixture fx = newFixture();
        TarifVignetteMoto tarifPartage = tarif(3750, 1500);
        when(fx.tarifVignetteMotoService().resoudre(eq(CategorieAgeMoto.AGE_5_10_ANS), eq(CategorieCylindree.MOINS_125_CM3), any()))
                .thenReturn(tarifPartage);

        DemandeVignetteMotoResponse response = fx.service().creer(requestNouvelleDemande());
        assertEquals(0, BigDecimal.valueOf(3750).compareTo(response.montantVignette()));

        // Simule un admin qui change le tarif APRES la creation de la demande (meme objet cote source,
        // pire cas possible pour une fuite de reference).
        tarifPartage.setMontantVignette(BigDecimal.valueOf(99999));
        tarifPartage.setFraisOperation(BigDecimal.valueOf(88888));

        assertEquals(0, BigDecimal.valueOf(3750).compareTo(response.montantVignette()));
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(response.fraisOperation()));
        assertEquals(0, BigDecimal.valueOf(6750).compareTo(response.montantTotal()));
    }

    @Test
    void creer_cylindreeZeroOuNegative_leve400() {
        Fixture fx = newFixture();
        DemandeVignetteMotoRequest request = new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN", "Yamaha", "Crypton", "XXXXXXXXXXXX", 0, DATE_MISE_CIRCULATION, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().creer(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void creer_tarifIntrouvablePourCetteCategorie_propageLe404DuServiceTarif() {
        Fixture fx = newFixture();
        when(fx.tarifVignetteMotoService().resoudre(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun tarif"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().creer(requestNouvelleDemande()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- soumettre() (§7/§13/§14)

    @Test
    void soumettre_nouvelleDemandeAvecLes5DocumentsRequis_passeEnAttentePaiement() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(eq("VIGNETTE_MOTO_DEMANDE"), eq(1L), anyInt(), anyInt()))
                .thenReturn(pageOf(document("CNI_ASSURE"), document("RECU_ACHAT_MOTO"),
                        document("PHOTO_MOTO"), document("PHOTO_CHASSIS"), document("ASSURANCE")));

        DemandeVignetteMotoResponse response = fx.service().soumettre(1L);

        assertEquals(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, response.statut());
    }

    @Test
    void soumettre_nouvelleDemandeSansAucunDocument_leve409AvecLes5TypesManquants() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(any(), any(), anyInt(), anyInt())).thenReturn(pageOf());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().soumettre(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("CNI_ASSURE"));
        assertTrue(ex.getReason().contains("RECU_ACHAT_MOTO"));
        assertTrue(ex.getReason().contains("PHOTO_MOTO"));
        assertTrue(ex.getReason().contains("PHOTO_CHASSIS"));
        assertTrue(ex.getReason().contains("ASSURANCE"));
    }

    @Test
    void soumettre_nouvelleDemandeAvecSeulementCniEtRecu_leve409SurLes3NouveauxTypes() {
        // Confirme que les 3 nouveaux documents (2026-09-18) sont bien devenus obligatoires pour une
        // NOUVELLE_DEMANDE, pas seulement optionnels/ignores.
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(document("CNI_ASSURE"), document("RECU_ACHAT_MOTO")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().soumettre(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("PHOTO_MOTO"));
        assertTrue(ex.getReason().contains("PHOTO_CHASSIS"));
        assertTrue(ex.getReason().contains("ASSURANCE"));
        assertTrue(!ex.getReason().contains("CNI_ASSURE"));
    }

    @Test
    void soumettre_renouvellementAvecCniEtRecuMaisSansAncienneVignette_leve409SurCeSeulType() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.RENOUVELLEMENT);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(document("CNI_ASSURE"), document("RECU_ACHAT_MOTO")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().soumettre(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("ANCIENNE_VIGNETTE"));
        assertTrue(!ex.getReason().contains("CNI_ASSURE"));
    }

    @Test
    void soumettre_renouvellementAvecLes3Documents_passeEnAttentePaiement() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.RENOUVELLEMENT);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(document("CNI_ASSURE"), document("RECU_ACHAT_MOTO"), document("ANCIENNE_VIGNETTE")));

        DemandeVignetteMotoResponse response = fx.service().soumettre(1L);
        assertEquals(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, response.statut());
    }

    @Test
    void soumettre_demandeDejaSoumise_leve409_pasDeResoumission() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().soumettre(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- ajouterDocument()

    @Test
    void ajouterDocument_typeInconnu_leve400() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE)));
        when(fx.gedDocumentTypeRepository().findByCodeIgnoreCase("INCONNU")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().ajouterDocument(1L, "INCONNU", null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- annuler() / rejeter()

    @Test
    void annuler_demandeEnBrouillon_passeEnAnnulee() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE)));

        DemandeVignetteMotoResponse response = fx.service().annuler(1L);
        assertEquals(StatutDemandeVignetteMoto.ANNULEE, response.statut());
    }

    @Test
    void annuler_demandeDejaLivree_leve409() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.LIVREE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().annuler(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void rejeter_demandeEnBrouillon_passeEnRejeteeAvecLeMotif() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE)));

        DemandeVignetteMotoResponse response = fx.service().rejeter(1L, "Chassis illisible");

        assertEquals(StatutDemandeVignetteMoto.REJETEE, response.statut());
        assertEquals("Chassis illisible", response.motifRejet());
    }

    @Test
    void rejeter_demandeDejaLivree_leve409_statutTerminal() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.LIVREE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().rejeter(1L, "motif"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void rejeter_demandeDejaPayee_estDesormaisAutorise_rejetGeneriqueAToutEtape() {
        // Decision produit du 2026-09-17 : REJETEE reste generique pour tout echec ulterieur
        // (paiement/edition/livraison), rejetable depuis n'importe quel statut non terminal - avant
        // cette date, seul BROUILLON/EN_ATTENTE_PAIEMENT etait autorise.
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.PAYEE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        DemandeVignetteMotoResponse response = fx.service().rejeter(1L, "Edition impossible");

        assertEquals(StatutDemandeVignetteMoto.REJETEE, response.statut());
        assertEquals("Edition impossible", response.motifRejet());
    }

    @Test
    void rejeter_parLeCreateurLuiMemeNonAdmin_leve403_confirmeParLeFrontendLeMemeJour() {
        // Contrairement a annuler(), rejeter sa propre demande n'a pas de sens pour un createur - le
        // frontend a confirme ce point le 2026-09-12 et demande une restriction ADMIN (pas createur).
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().rejeter(1L, "motif"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        org.mockito.Mockito.verify(fx.repository(), org.mockito.Mockito.never()).save(any());
    }

    // ---------------------------------------------------------------- garde-fou IDOR (audit de securite
    // du 2026-09-12, ajuste le meme jour suite au retour du frontend : self-service par createur,
    // pas ADMIN-only) - cf. verifierAccesDemandeCible().

    @Test
    void creer_enregistreLeCreateurCourant() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");

        fx.service().creer(requestNouvelleDemande());

        org.mockito.ArgumentCaptor<DemandeVignetteMoto> captor = org.mockito.ArgumentCaptor.forClass(DemandeVignetteMoto.class);
        org.mockito.Mockito.verify(fx.repository()).save(captor.capture());
        assertEquals("jean.kouassi.test", captor.getValue().getCreatedBy());
    }

    @Test
    void get_createurDeLaDemande_estAutorise() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        DemandeVignetteMotoResponse response = fx.service().get(1L);

        assertEquals(1L, response.id());
    }

    @Test
    void get_nonCreateurEtNonAdmin_estRejeteAvec404_pasDeFuiteDexistence() {
        Fixture fx = newFixture("autre.utilisateur", "COMMERCIAL");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().get(1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void get_admin_accedeMemeSansEtreLeCreateur() {
        Fixture fx = newFixture("service", "ADMIN");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        assertEquals(1L, fx.service().get(1L).id());
    }

    @Test
    void list_nonAdmin_neVoitQueSesPropresDemandes() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");
        DemandeVignetteMoto laSienne = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        laSienne.setCreatedBy("jean.kouassi.test");
        DemandeVignetteMoto celleDunAutre = demandeEnBrouillon(2L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        celleDunAutre.setCreatedBy("autre.utilisateur");
        when(fx.repository().findAllByOrderByCreatedAtDesc()).thenReturn(List.of(laSienne, celleDunAutre));

        PageResponse<DemandeVignetteMotoResponse> response = fx.service().list(null, 0, 20);

        assertEquals(1, response.totalElements());
        assertEquals(1L, response.content().get(0).id());
    }

    @Test
    void downloadDocument_documentLieALaDemandeEtCreateurAutorise_reussit() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        GedDocumentResponse doc = document("CNI_ASSURE");
        when(fx.gedDocumentService().listByEntity(eq("VIGNETTE_MOTO_DEMANDE"), eq(1L), anyInt(), anyInt())).thenReturn(pageOf(doc));
        when(fx.gedDocumentService().download(1L)).thenReturn(new GedDocumentVersionContent("contenu".getBytes(), "application/pdf", "cni.pdf"));

        GedDocumentVersionContent content = fx.service().downloadDocument(1L, 1L);

        assertEquals("cni.pdf", content.fileName());
    }

    @Test
    void downloadDocument_documentNonLieACetteDemande_estRejeteAvec404_memeSiLeCreateurEstBonEtLeDocumentExiste() {
        // Empeche un createur legitime de sa PROPRE demande de recuperer un document appartenant a une
        // demande D'AUTRUI simplement en devinant/incrementant un documentId.
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setCreatedBy("jean.kouassi.test");
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        when(fx.gedDocumentService().listByEntity(eq("VIGNETTE_MOTO_DEMANDE"), eq(1L), anyInt(), anyInt())).thenReturn(pageOf());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().downloadDocument(1L, 999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        org.mockito.Mockito.verify(fx.gedDocumentService(), org.mockito.Mockito.never()).download(org.mockito.ArgumentMatchers.anyLong());
    }

    // ---------------------------------------------------------------- Apporteur + commission (2026-09-15)

    private DemandeVignetteMotoRequest requestAvecPartenaireApporteur(Long partenaireId) {
        return new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", 110, DATE_MISE_CIRCULATION, partenaireId, null);
    }

    private DemandeVignetteMotoRequest requestAvecCommercialApporteur(Long commercialId) {
        return new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", 110, DATE_MISE_CIRCULATION, null, commercialId);
    }

    @Test
    void creer_partenaireEtCommercialApporteursTousLesDeux_leve400() {
        Fixture fx = newFixture();
        DemandeVignetteMotoRequest request = new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", 110, DATE_MISE_CIRCULATION, 5L, 9L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().creer(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(fx.vignetteCommissionRepository());
    }

    @Test
    void creer_partenaireApporteurInconnu_leve404_etNeCreeAucuneDemandeNiCommission() {
        Fixture fx = newFixture();
        when(fx.partenaireRepository().findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().creer(requestAvecPartenaireApporteur(5L)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        org.mockito.Mockito.verify(fx.repository(), org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(fx.vignetteCommissionRepository());
    }

    @Test
    void creer_avecPartenaireApporteurZoneAbidjan_creeUneCommissionEtDeduitDuMontantTotal() {
        // Contrat "Commission apporteur variable par zone", 2026-09-18 : requestAvecPartenaireApporteur
        // livre en ABIDJAN -> montant zone ABIDJAN, deduit du montantTotal facture au client.
        Fixture fx = newFixture();
        ci.assurzen.entities.Partenaire partenaire = ci.assurzen.entities.Partenaire.builder().id(5L)
                .nom("Yao").prenoms("Marie").telephone("0700000001").build();
        when(fx.partenaireRepository().findById(5L)).thenReturn(Optional.of(partenaire));
        when(fx.dynamicConfigurationService().getLong(eq("VIGNETTE_COMMISSION_APPORTEUR_MONTANT_ABIDJAN"), any()))
                .thenReturn(500L);

        DemandeVignetteMotoResponse response = fx.service().creer(requestAvecPartenaireApporteur(5L));

        org.mockito.ArgumentCaptor<ci.assurzen.entities.VignetteCommission> captor =
                org.mockito.ArgumentCaptor.forClass(ci.assurzen.entities.VignetteCommission.class);
        org.mockito.Mockito.verify(fx.vignetteCommissionRepository()).save(captor.capture());
        assertEquals(partenaire, captor.getValue().getPartenaire());
        assertEquals(null, captor.getValue().getCommercial());
        assertEquals(500L, captor.getValue().getMontant());
        assertEquals(CommissionStatus.pending, captor.getValue().getStatut());

        // Tarif de reference (§10) = 6750 sans apporteur -> 6250 avec la reduction de 500.
        assertEquals(0, BigDecimal.valueOf(500).compareTo(response.reductionApporteur()));
        assertEquals(0, BigDecimal.valueOf(6250).compareTo(response.montantTotal()));
    }

    @Test
    void creer_avecCommercialApporteurZoneHorsAbidjan_utiliseLeMontantHorsAbidjan() {
        Fixture fx = newFixture();
        ci.assurzen.entities.Profile profile = ci.assurzen.entities.Profile.builder().id(1L)
                .firstname("Jean").lastname("Koffi").phone("0700000002").build();
        ci.assurzen.entities.Commercial commercial = ci.assurzen.entities.Commercial.builder().id(9L)
                .employeeCode("COM-000009").profile(profile).build();
        when(fx.commercialRepository().findById(9L)).thenReturn(Optional.of(commercial));
        when(fx.fraisLivraisonZoneService().resoudre("HORS_ABIDJAN")).thenReturn(zone("HORS_ABIDJAN", 2000));
        when(fx.dynamicConfigurationService().getLong(eq("VIGNETTE_COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN"), any()))
                .thenReturn(1000L);

        DemandeVignetteMotoRequest request = new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "HORS_ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", 110, DATE_MISE_CIRCULATION, null, 9L);

        DemandeVignetteMotoResponse response = fx.service().creer(request);

        org.mockito.ArgumentCaptor<ci.assurzen.entities.VignetteCommission> captor =
                org.mockito.ArgumentCaptor.forClass(ci.assurzen.entities.VignetteCommission.class);
        org.mockito.Mockito.verify(fx.vignetteCommissionRepository()).save(captor.capture());
        assertEquals(commercial, captor.getValue().getCommercial());
        assertEquals(null, captor.getValue().getPartenaire());
        assertEquals(1000L, captor.getValue().getMontant());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(response.reductionApporteur()));
    }

    @Test
    void creer_sansApporteur_neCreeAucuneCommission() {
        Fixture fx = newFixture();

        DemandeVignetteMotoResponse response = fx.service().creer(requestNouvelleDemande());

        org.mockito.Mockito.verifyNoInteractions(fx.vignetteCommissionRepository());
        assertEquals(null, response.reductionApporteur());
        assertEquals(0, BigDecimal.valueOf(6750).compareTo(response.montantTotal()));
    }

    // ---------------------------------------------------------------- Suivi d'evolution + lien public (2026-09-17)

    @Test
    void creer_genereUnLienDeSuiviEtEnvoieLeSmsDeCreationAvecHistoriquePremierEvenement() {
        Fixture fx = newFixture();
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000000"), eq("VGN_MOTO_BROUILLON"), any(), any()))
                .thenReturn(true);

        fx.service().creer(requestNouvelleDemande());

        org.mockito.Mockito.verify(fx.lienSuiviVignetteService()).genererPourDemande(any());
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> variablesCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(fx.smsNotificationService()).sendFromTemplate(
                eq("0700000000"), eq("VGN_MOTO_BROUILLON"), variablesCaptor.capture(), eq(ci.assurzen.entities.TypeMessageSms.vignette_moto_changement_statut));
        assertEquals("https://suivi.test/vignette?token=tokenBrutTest", variablesCaptor.getValue().get("lienSuivi"));

        org.mockito.ArgumentCaptor<ci.assurzen.entities.HistoriqueDemandeVignetteMoto> historiqueCaptor =
                org.mockito.ArgumentCaptor.forClass(ci.assurzen.entities.HistoriqueDemandeVignetteMoto.class);
        org.mockito.Mockito.verify(fx.historiqueRepository()).save(historiqueCaptor.capture());
        assertEquals(null, historiqueCaptor.getValue().getAncienStatut());
        assertEquals(StatutDemandeVignetteMoto.BROUILLON, historiqueCaptor.getValue().getNouveauStatut());
        assertEquals(Boolean.TRUE, historiqueCaptor.getValue().getNotificationEnvoyee());
    }

    @Test
    void changerStatut_progressionValideParUnAdmin_metAJourLeStatutEtHistoriseAvecMotifEtReference() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        DemandeVignetteMotoResponse response = fx.service().changerStatut(1L, StatutDemandeVignetteMoto.PAYEE, null, "PAY-REF-001");

        assertEquals(StatutDemandeVignetteMoto.PAYEE, response.statut());
        org.mockito.ArgumentCaptor<ci.assurzen.entities.HistoriqueDemandeVignetteMoto> historiqueCaptor =
                org.mockito.ArgumentCaptor.forClass(ci.assurzen.entities.HistoriqueDemandeVignetteMoto.class);
        org.mockito.Mockito.verify(fx.historiqueRepository()).save(historiqueCaptor.capture());
        assertEquals(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, historiqueCaptor.getValue().getAncienStatut());
        assertEquals(StatutDemandeVignetteMoto.PAYEE, historiqueCaptor.getValue().getNouveauStatut());
        assertEquals("PAY-REF-001", historiqueCaptor.getValue().getReferenceOperation());
    }

    @Test
    void changerStatut_transitionNonAutoriseeParLaMatrice_leve409() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        demande.setStatut(StatutDemandeVignetteMoto.BROUILLON);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        // BROUILLON -> EN_TRAITEMENT n'est pas dans la matrice (il faut passer par EN_ATTENTE_PAIEMENT/PAYEE d'abord).
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, StatutDemandeVignetteMoto.EN_TRAITEMENT, null, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void changerStatut_cibleNonAtteignableViaCetEndpoint_leve400() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));

        // ANNULEE a son propre endpoint dedie (annuler()), pas atteignable via changerStatut().
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, StatutDemandeVignetteMoto.ANNULEE, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void changerStatut_nonAdmin_leve403() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, StatutDemandeVignetteMoto.PAYEE, null, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        org.mockito.Mockito.verify(fx.repository(), org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void getHistorique_retourneLesEvenementsDansLOrdreChronologique() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(demande));
        ci.assurzen.entities.HistoriqueDemandeVignetteMoto evt1 = ci.assurzen.entities.HistoriqueDemandeVignetteMoto.builder()
                .id(1L).demandeVignetteMoto(demande).nouveauStatut(StatutDemandeVignetteMoto.BROUILLON).build();
        ci.assurzen.entities.HistoriqueDemandeVignetteMoto evt2 = ci.assurzen.entities.HistoriqueDemandeVignetteMoto.builder()
                .id(2L).demandeVignetteMoto(demande).ancienStatut(StatutDemandeVignetteMoto.BROUILLON)
                .nouveauStatut(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT).build();
        when(fx.historiqueRepository().findByDemandeVignetteMotoIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(evt1, evt2));

        List<HistoriqueDemandeVignetteMotoResponse> historique = fx.service().getHistorique(1L);

        assertEquals(2, historique.size());
        assertEquals(StatutDemandeVignetteMoto.BROUILLON, historique.get(0).nouveauStatut());
        assertEquals(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, historique.get(1).nouveauStatut());
    }

    @Test
    void getSuiviPublic_resoutLeTokenEtConstruitLaReponseAvecLibelles() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demandeEnBrouillon(1L, TypeDemandeVignette.NOUVELLE_DEMANDE);
        when(fx.lienSuiviVignetteService().resoudreDemandeActive("tok123")).thenReturn(demande);
        ci.assurzen.entities.HistoriqueDemandeVignetteMoto evt1 = ci.assurzen.entities.HistoriqueDemandeVignetteMoto.builder()
                .id(1L).demandeVignetteMoto(demande).nouveauStatut(StatutDemandeVignetteMoto.BROUILLON).createdAt(Instant.now()).build();
        when(fx.historiqueRepository().findByDemandeVignetteMotoIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(evt1));

        var reponse = fx.service().getSuiviPublic("tok123");

        assertEquals("VGN2026000001", reponse.reference());
        assertEquals(StatutDemandeVignetteMoto.BROUILLON, reponse.statut());
        assertEquals("Demande enregistree", reponse.libelleStatut());
        assertEquals(1, reponse.historique().size());
    }

    // ------------------------------------------------------------------

    private DemandeVignetteMoto demandeEnBrouillon(long id, TypeDemandeVignette typeDemande) {
        return DemandeVignetteMoto.builder().id(id).reference("VGN2026000001")
                .typeDemande(typeDemande).statut(StatutDemandeVignetteMoto.BROUILLON)
                .nomAssure("KOUASSI").prenomsAssure("Jean").telephoneAssure("0700000000").adresseLivraison("Cocody").villeLivraison("Abidjan")
                .marqueMoto("Yamaha").modeleMoto("Crypton").numeroChassisMoto("XXXXXXXXXXXX")
                .cylindreeMoto(110).datePremiereMiseCirculation(DATE_MISE_CIRCULATION)
                .categorieAgeAppliquee(CategorieAgeMoto.AGE_5_10_ANS).categorieCylindreeAppliquee(CategorieCylindree.MOINS_125_CM3)
                .ageMotoCalcule(6).montantVignette(BigDecimal.valueOf(3750)).fraisOperation(BigDecimal.valueOf(1500))
                .fraisLivraison(BigDecimal.valueOf(1500)).fraisAccessoires(BigDecimal.valueOf(3000)).montantTotal(BigDecimal.valueOf(6750))
                .build();
    }
}
