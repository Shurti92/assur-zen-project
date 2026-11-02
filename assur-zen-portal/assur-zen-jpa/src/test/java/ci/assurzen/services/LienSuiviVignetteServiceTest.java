package ci.assurzen.services;

import ci.assurzen.dto.LienSuiviVignetteResponse;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.LienSuiviVignette;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.LienSuiviVignetteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le cycle de vie du token de suivi public (contrat "Suivi public securise par token",
 * 2026-09-17) : le point critique est que le token brut n'est JAMAIS retrouvable a partir du hash
 * persiste (SHA-256 deterministe, pas BCrypt - cf. javadoc LienSuiviVignette), et que le suivi public
 * ne distingue jamais un token inexistant d'un token expire/revoque (meme exception generique).
 */
class LienSuiviVignetteServiceTest {

    private record Fixture(
            LienSuiviVignetteService service,
            LienSuiviVignetteRepository repository,
            DemandeVignetteMotoRepository demandeVignetteMotoRepository,
            DynamicConfigurationService dynamicConfigurationService,
            AuthService authService,
            SmsNotificationService smsNotificationService
    ) {
    }

    private Fixture newFixture() {
        return newFixture("service", "ADMIN");
    }

    private Fixture newFixture(String username, String roles) {
        LienSuiviVignetteRepository repository = mock(LienSuiviVignetteRepository.class);
        DemandeVignetteMotoRepository demandeVignetteMotoRepository = mock(DemandeVignetteMotoRepository.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        AuthService authService = mock(AuthService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, username, roles));
        when(dynamicConfigurationService.getLong(eq("VIGNETTE_SUIVI_TOKEN_VALIDITE_JOURS"), any())).thenReturn(90L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LienSuiviVignetteService service = new LienSuiviVignetteService(
                repository, demandeVignetteMotoRepository, dynamicConfigurationService, authService, smsNotificationService);
        ReflectionTestUtils.setField(service, "suiviLienBaseUrl", "https://suivi.test/vignette");

        return new Fixture(service, repository, demandeVignetteMotoRepository, dynamicConfigurationService, authService, smsNotificationService);
    }

    private DemandeVignetteMoto demande(long id) {
        return DemandeVignetteMoto.builder().id(id).reference("VGN2026000001").telephoneAssure("0700000000").build();
    }

    @Test
    void genererPourDemande_produitUnTokenExploitableEtUnLienComplet() {
        Fixture fx = newFixture();

        LienSuiviVignetteResponse reponse = fx.service().genererPourDemande(demande(1L));

        assertEquals("https://suivi.test/vignette?token=" + reponse.tokenBrut(), reponse.lienComplet());
        assertFalse(reponse.tokenBrut().isBlank());
    }

    @Test
    void genererPourDemande_neStockeJamaisLeTokenBrutEnClair() {
        Fixture fx = newFixture();

        LienSuiviVignetteResponse reponse = fx.service().genererPourDemande(demande(1L));

        org.mockito.ArgumentCaptor<LienSuiviVignette> captor = org.mockito.ArgumentCaptor.forClass(LienSuiviVignette.class);
        verify(fx.repository()).save(captor.capture());
        assertNotEquals(reponse.tokenBrut(), captor.getValue().getTokenHash());
        assertEquals(64, captor.getValue().getTokenHash().length()); // SHA-256 hex = 64 caracteres
    }

    @Test
    void resoudreDemandeActive_tokenValide_retourneLaDemandeEtMetAJourLaDerniereUtilisation() {
        Fixture fx = newFixture();
        LienSuiviVignetteResponse genere = fx.service().genererPourDemande(demande(1L));
        // Simule la relecture par hash : reconstruit l'entite telle qu'elle a ete sauvegardee et la
        // renvoie via findByTokenHash (le test capture le hash reellement calcule a la generation).
        org.mockito.ArgumentCaptor<LienSuiviVignette> captor = org.mockito.ArgumentCaptor.forClass(LienSuiviVignette.class);
        verify(fx.repository()).save(captor.capture());
        LienSuiviVignette lienPersiste = captor.getValue();
        lienPersiste.setDemandeVignetteMoto(demande(1L));
        when(fx.repository().findByTokenHash(lienPersiste.getTokenHash())).thenReturn(Optional.of(lienPersiste));

        DemandeVignetteMoto resultat = fx.service().resoudreDemandeActive(genere.tokenBrut());

        assertEquals(1L, resultat.getId());
        assertNotEquals(null, lienPersiste.getDateDerniereUtilisation());
    }

    @Test
    void resoudreDemandeActive_tokenInconnu_leve404Generique() {
        Fixture fx = newFixture();
        when(fx.repository().findByTokenHash(anyString())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudreDemandeActive("nimporte-quoi"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void resoudreDemandeActive_tokenExpire_leve404GeneriqueIdentiqueAUnTokenInconnu() throws Exception {
        Fixture fx = newFixture();
        String tokenBrut = "token-expire";
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(tokenBrut.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        LienSuiviVignette lienExpire = LienSuiviVignette.builder()
                .id(1L).demandeVignetteMoto(demande(1L)).tokenHash(hash)
                .dateExpiration(Instant.now().minusSeconds(60)).actif(true).build();
        when(fx.repository().findByTokenHash(hash)).thenReturn(Optional.of(lienExpire));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudreDemandeActive(tokenBrut));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void resoudreDemandeActive_tokenRevoque_leve404GeneriqueIdentiqueAUnTokenInconnu() {
        Fixture fx = newFixture();
        LienSuiviVignetteResponse genere = fx.service().genererPourDemande(demande(1L));
        org.mockito.ArgumentCaptor<LienSuiviVignette> captor = org.mockito.ArgumentCaptor.forClass(LienSuiviVignette.class);
        verify(fx.repository()).save(captor.capture());
        LienSuiviVignette lienRevoque = captor.getValue();
        lienRevoque.setActif(false);
        when(fx.repository().findByTokenHash(lienRevoque.getTokenHash())).thenReturn(Optional.of(lienRevoque));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().resoudreDemandeActive(genere.tokenBrut()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void regenerer_desactiveLAncienLienEtEnvoieLeNouveauParSms() {
        Fixture fx = newFixture();
        DemandeVignetteMoto demande = demande(1L);
        when(fx.demandeVignetteMotoRepository().findById(1L)).thenReturn(Optional.of(demande));
        LienSuiviVignette ancien = LienSuiviVignette.builder().id(1L).demandeVignetteMoto(demande)
                .tokenHash("ancien-hash").dateExpiration(Instant.now().plusSeconds(3600)).actif(true).build();
        when(fx.repository().findByDemandeVignetteMotoIdAndActifTrue(1L)).thenReturn(Optional.of(ancien));

        LienSuiviVignetteResponse nouveau = fx.service().regenerer(1L);

        assertFalse(ancien.isActif());
        verify(fx.smsNotificationService()).sendFromTemplate(eq("0700000000"), eq("VGN_MOTO_LIEN_REGENERE"), any(), any());
        assertEquals("https://suivi.test/vignette?token=" + nouveau.tokenBrut(), nouveau.lienComplet());
    }

    @Test
    void regenerer_nonAdmin_leve403_neCreeAucunLien() {
        Fixture fx = newFixture("jean.kouassi.test", "COMMERCIAL");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().regenerer(1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.repository(), never()).save(any());
    }

    @Test
    void revoquer_lienActifExistant_leDesactiveAvecLeMotif() {
        Fixture fx = newFixture();
        LienSuiviVignette actif = LienSuiviVignette.builder().id(1L).demandeVignetteMoto(demande(1L))
                .tokenHash("hash").dateExpiration(Instant.now().plusSeconds(3600)).actif(true).build();
        when(fx.repository().findByDemandeVignetteMotoIdAndActifTrue(1L)).thenReturn(Optional.of(actif));

        fx.service().revoquer(1L, "Lien compromis");

        assertFalse(actif.isActif());
        assertEquals("Lien compromis", actif.getMotifRevocation());
    }

    @Test
    void revoquer_aucunLienActif_leve404() {
        Fixture fx = newFixture();
        when(fx.repository().findByDemandeVignetteMotoIdAndActifTrue(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().revoquer(1L, "motif"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
