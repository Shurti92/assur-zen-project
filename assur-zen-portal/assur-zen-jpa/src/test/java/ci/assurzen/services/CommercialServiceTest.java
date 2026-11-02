package ci.assurzen.services;

import ci.assurzen.dto.CommercialRequest;
import ci.assurzen.dto.CommercialResponse;
import ci.assurzen.dto.CommercialStatusRequest;
import ci.assurzen.dto.CommercialUpdateRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.UserProfileRequest;
import ci.assurzen.dto.UserProfileResponse;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre la creation (delegation a UserProfileService, garde-fous sur les avantages), le changement
 * de statut (desactivation/reactivation du compte de connexion, §9), et le controle d'acces
 * proprietaire/administrateur (§60/§61 - jamais le salaire d'un autre commercial).
 */
class CommercialServiceTest {

    private record Fixture(
            CommercialService service,
            CommercialRepository repository,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            VillePartenaireRepository villePartenaireRepository,
            UserProfileService userProfileService,
            CodeGeneratorService codeGeneratorService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder
    ) {
    }

    private Fixture newFixture() {
        CommercialRepository repository = mock(CommercialRepository.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        VillePartenaireRepository villePartenaireRepository = mock(VillePartenaireRepository.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(codeGeneratorService.generate(any())).thenReturn("COM2026000001");
        when(repository.save(any())).thenAnswer(inv -> {
            Commercial c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommercialService service = new CommercialService(repository, profileRepository, userRepository,
                villePartenaireRepository, userProfileService, codeGeneratorService, authService, functionalAuditRecorder);

        return new Fixture(service, repository, profileRepository, userRepository, villePartenaireRepository,
                userProfileService, codeGeneratorService, authService, functionalAuditRecorder);
    }

    private void commePrincipalNonAdmin(Fixture fx, long userId) {
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(userId, "commercial" + userId, "COMMERCIAL"));
    }

    private Commercial commercial(long id, long userId, StatutCommercial statut, Instant createdAt) {
        User user = new User();
        user.setId(userId);
        user.setUsername("commercial" + userId);
        user.setActive(true);
        Profile profile = Profile.builder().id(500 + id).firstname("Awa").lastname("Bamba")
                .email("awa@example.com").phone("0700000000").user(user).build();
        return Commercial.builder().id(id).employeeCode("COM-0000" + id).profile(profile)
                .baseSalary(BigDecimal.valueOf(300000)).transportBenefitAmount(BigDecimal.ZERO)
                .phoneAllowanceAmount(BigDecimal.ZERO).statut(statut).hireDate(LocalDate.of(2026, 1, 1))
                .createdAt(createdAt).updatedAt(createdAt).build();
    }

    private UserProfileResponse userProfileResponse(Long profileId, String temporaryPassword, Boolean notificationEnvoyee) {
        return new UserProfileResponse(10L, "jkoffi", "COMMERCIAL", true, profileId, "PRF001",
                "Jean", "Koffi", "0700000000", "COMMERCIAL", "jean.koffi@example.com", null, true, 77L,
                null, null, null, null, null, null, null, null, null, null, true,
                temporaryPassword, notificationEnvoyee, null);
    }

    private CommercialRequest createRequest(boolean transportEnabled, BigDecimal transportAmount, boolean phoneEnabled, BigDecimal phoneAmount) {
        return new CommercialRequest(null, "jkoffi", null, "Jean", "Koffi", "jean.koffi@example.com", "0700000000",
                77L, BigDecimal.valueOf(300000), transportEnabled, transportAmount, phoneEnabled, phoneAmount,
                LocalDate.of(2026, 1, 1));
    }

    // ---------------------------------------------------------------- options() (souscription, 2026-10-02)

    @Test
    void options_listeLesCommerciauxActifsAvecCompte_trieParNomSansRemuneration_memeSansDroitAdmin() {
        Fixture fx = newFixture();
        commePrincipalNonAdmin(fx, 42L);
        Commercial koffi = commercial(1, 101, StatutCommercial.ACTIVE, Instant.now());
        koffi.getProfile().setLastname("Koffi");
        koffi.getProfile().setFirstname("Yao");
        Commercial bamba = commercial(2, 102, StatutCommercial.ACTIVE, Instant.now());
        Commercial sansCompte = commercial(3, 103, StatutCommercial.ACTIVE, Instant.now());
        sansCompte.getProfile().setUser(null);
        when(fx.repository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(java.util.List.of(koffi, sansCompte, bamba));

        java.util.List<ci.assurzen.dto.CommercialOptionResponse> options = fx.service().options();

        assertEquals(2, options.size());
        assertEquals("Bamba", options.get(0).nom());
        assertEquals("Awa", options.get(0).prenoms());
        assertEquals(102L, options.get(0).userId());
        assertEquals("Koffi", options.get(1).nom());
    }

    // ---------------------------------------------------------------- create()

    @Test
    void create_succes_delegueAUserProfileServiceAvecLeRoleCommercial() {
        Fixture fx = newFixture();
        when(fx.repository().existsByEmployeeCode("COM2026000001")).thenReturn(false);
        when(fx.userProfileService().create(any())).thenReturn(userProfileResponse(99L, "Xk7!mQ2p", true));
        Profile profileApresCreation = Profile.builder().id(99L).firstname("Jean").lastname("Koffi")
                .email("jean.koffi@example.com").phone("0700000000")
                .user(userWith(10L, "jkoffi"))
                .villePartenaire(VillePartenaire.builder().id(77L).nomVille("Abidjan").build())
                .build();
        when(fx.profileRepository().findById(99L)).thenReturn(Optional.of(profileApresCreation));

        CommercialResponse response = fx.service().create(createRequest(false, null, false, null));

        assertEquals("COM2026000001", response.employeeCode());
        assertEquals("Xk7!mQ2p", response.temporaryPassword());
        assertEquals(Boolean.TRUE, response.notificationEnvoyee());
        assertEquals(10L, response.userId());
        verify(fx.userProfileService()).create(argThat(req -> "COMMERCIAL".equals(req.roles()) && "jkoffi".equals(req.username())));
    }

    @Test
    void create_matriculeDejaExistant_estRejeteAvec409EtNeCreePasDeCompte() {
        Fixture fx = newFixture();
        CommercialRequest request = new CommercialRequest("COM-EXIST", "jkoffi", null, "Jean", "Koffi",
                "jean.koffi@example.com", "0700000000", 77L, BigDecimal.valueOf(300000), false, null, false, null,
                LocalDate.of(2026, 1, 1));
        when(fx.repository().existsByEmployeeCode("COM-EXIST")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.userProfileService(), never()).create(any());
    }

    @Test
    void create_avantageTransportActiveSansMontant_estRejeteAvec400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(createRequest(true, null, false, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(fx.codeGeneratorService(), never()).generate(any());
        verify(fx.userProfileService(), never()).create(any());
    }

    @Test
    void create_avantageTeleponeDesactive_forceLeMontantAZeroMemeSiUnMontantEstFourni() {
        Fixture fx = newFixture();
        when(fx.repository().existsByEmployeeCode(any())).thenReturn(false);
        when(fx.userProfileService().create(any())).thenReturn(userProfileResponse(99L, "pwd", true));
        Profile profile = Profile.builder().id(99L).firstname("Jean").lastname("Koffi")
                .email("jean.koffi@example.com").phone("0700000000").user(userWith(10L, "jkoffi")).build();
        when(fx.profileRepository().findById(99L)).thenReturn(Optional.of(profile));

        // phoneAllowanceEnabled=false mais un montant positif est quand meme fourni par erreur cote appelant.
        CommercialResponse response = fx.service().create(createRequest(false, null, false, BigDecimal.valueOf(50000)));

        assertFalse(response.phoneAllowanceEnabled());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.phoneAllowanceAmount()));
    }

    @Test
    void create_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        commePrincipalNonAdmin(fx, 5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(createRequest(false, null, false, null)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.userProfileService(), never()).create(any());
    }

    // ---------------------------------------------------------------- update()

    @Test
    void update_succes_metAJourLeProfilEtLaRemunerationEtTraceLaudit() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));
        VillePartenaire ville = VillePartenaire.builder().id(80L).nomVille("Bouake").build();
        when(fx.villePartenaireRepository().findById(80L)).thenReturn(Optional.of(ville));

        CommercialUpdateRequest request = new CommercialUpdateRequest("Awa", "Bamba-Traore", "awa.traore@example.com",
                "0708080808", 80L, BigDecimal.valueOf(350000), true, BigDecimal.valueOf(50000), false, null,
                LocalDate.of(2026, 1, 1), null);

        CommercialResponse response = fx.service().update(1L, request);

        assertEquals("Bamba-Traore", response.lastname());
        assertEquals(80L, response.villePartenaireId());
        assertEquals(0, BigDecimal.valueOf(350000).compareTo(response.baseSalary()));
        assertTrue(response.transportBenefitEnabled());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.transportBenefitAmount()));
        verify(fx.functionalAuditRecorder()).recordUpdate(eq("COMMERCIAL"), eq("Commercial"), eq("1"), any(), any(), any());
    }

    @Test
    void update_avantageTransportActiveSansMontant_estRejeteAvec400() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        CommercialUpdateRequest request = new CommercialUpdateRequest("Awa", "Bamba", "awa@example.com",
                "0708080808", 80L, BigDecimal.valueOf(350000), true, null, false, null, LocalDate.of(2026, 1, 1), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void update_localiteIntrouvable_estRejeteAvec404() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));
        when(fx.villePartenaireRepository().findById(999L)).thenReturn(Optional.empty());

        CommercialUpdateRequest request = new CommercialUpdateRequest("Awa", "Bamba", "awa@example.com",
                "0708080808", 999L, BigDecimal.valueOf(350000), false, null, false, null, LocalDate.of(2026, 1, 1), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(1L, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- changerStatut() (§9)

    @Test
    void changerStatut_versSuspended_desactiveLeCompteDeConnexion() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        fx.service().changerStatut(1L, new CommercialStatusRequest(StatutCommercial.SUSPENDED, "sanction"));

        assertFalse(c.getProfile().getUser().isActive());
        verify(fx.userRepository()).save(c.getProfile().getUser());
    }

    @Test
    void changerStatut_versActive_reactiveLeCompteDeConnexion() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.SUSPENDED, Instant.now());
        c.getProfile().getUser().setActive(false);
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        fx.service().changerStatut(1L, new CommercialStatusRequest(StatutCommercial.ACTIVE, null));

        assertTrue(c.getProfile().getUser().isActive());
    }

    @Test
    void changerStatut_versTerminated_renseigneLaDateDeFinSiAbsente() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        CommercialResponse response = fx.service().changerStatut(1L, new CommercialStatusRequest(StatutCommercial.TERMINATED, "depart"));

        assertEquals(StatutCommercial.TERMINATED, response.statut());
        assertEquals(LocalDate.now(), response.endDate());
    }

    @Test
    void changerStatut_depuisTerminated_estRejeteAvec409_statutTerminal() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.TERMINATED, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, new CommercialStatusRequest(StatutCommercial.ACTIVE, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.userRepository(), never()).save(any());
    }

    // ---------------------------------------------------------------- Controle d'acces (§60/§61)

    @Test
    void get_parUnAdministrateur_reussitPourNimporteQuelCommercial() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));

        CommercialResponse response = fx.service().get(1L);

        assertEquals(1L, response.id());
    }

    @Test
    void get_parLeCommercialLuiMeme_reussit() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findById(1L)).thenReturn(Optional.of(c));
        when(fx.repository().findByProfile_UserId(10L)).thenReturn(Optional.of(c));
        commePrincipalNonAdmin(fx, 10L);

        CommercialResponse response = fx.service().get(1L);

        assertEquals(1L, response.id());
    }

    @Test
    void get_parUnAutreCommercialNonAdmin_estRejeteAvec403_neDivulguePasLeSalaire() {
        Fixture fx = newFixture();
        Commercial autre = commercial(2, 20, StatutCommercial.ACTIVE, Instant.now());
        when(fx.repository().findByProfile_UserId(5L)).thenReturn(Optional.of(autre));
        commePrincipalNonAdmin(fx, 5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().get(1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.repository(), never()).findById(1L);
    }

    // ---------------------------------------------------------------- me()

    @Test
    void me_resoutLaFicheViaLeUserIdDuPrincipalConnecte() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 10, StatutCommercial.ACTIVE, Instant.now());
        commePrincipalNonAdmin(fx, 10L);
        when(fx.repository().findByProfile_UserId(10L)).thenReturn(Optional.of(c));

        CommercialResponse response = fx.service().me();

        assertEquals(1L, response.id());
    }

    // ---------------------------------------------------------------- list()

    @Test
    void list_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        commePrincipalNonAdmin(fx, 5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().list(null, null, 0, 20));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void list_filtreParStatutEtTrieParDateDeCreationDecroissante() {
        Fixture fx = newFixture();
        Commercial ancien = commercial(1, 10, StatutCommercial.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"));
        Commercial recent = commercial(2, 20, StatutCommercial.ACTIVE, Instant.parse("2026-06-01T00:00:00Z"));
        Commercial suspendu = commercial(3, 30, StatutCommercial.SUSPENDED, Instant.parse("2026-03-01T00:00:00Z"));
        when(fx.repository().findAll()).thenReturn(List.of(ancien, recent, suspendu));

        PageResponse<CommercialResponse> page = fx.service().list(StatutCommercial.ACTIVE, null, 0, 20);

        assertEquals(2, page.totalElements());
        assertEquals(2L, page.content().get(0).id());
        assertEquals(1L, page.content().get(1).id());
    }

    private User userWith(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setActive(true);
        return user;
    }
}
