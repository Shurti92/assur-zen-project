package ci.assurzen.services;

import ci.assurzen.entities.DeviceType;
import ci.assurzen.entities.LoginHistory;
import ci.assurzen.repositories.LoginHistoryRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Detection d'une connexion depuis un appareil inconnu (2026-10-04). */
class LoginHistoryNouvelAppareilTest {

    private final LoginHistoryRepository repository = mock(LoginHistoryRepository.class);
    private final LoginHistoryService service = new LoginHistoryService(repository, mock(ProfileRepository.class),
            mock(PartenaireRepository.class), mock(UserAgentParserService.class), mock(GeoIpLookupService.class),
            mock(ApplicationEventPublisher.class));

    private static LoginHistoryService.ClientInfo client(DeviceType type, String navigateur, String os) {
        return new LoginHistoryService.ClientInfo("1.2.3.4", "ua", type, navigateur, os, null, "Abidjan", null);
    }

    @Test
    void famille_sansVersion() {
        assertEquals("Chrome", LoginHistoryService.famille("Chrome 141"));
        assertEquals("Windows", LoginHistoryService.famille("Windows 10"));
        assertEquals("Mac OS X", LoginHistoryService.famille("Mac OS X"));
    }

    @Test
    void premiereConnexion_pasDAlerte() {
        when(repository.findTop100ByUserIdAndEchecIsNullOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        assertFalse(service.estNouvelAppareil(7L, client(DeviceType.DESKTOP, "Chrome 141", "Windows 10")));
    }

    @Test
    void memeAppareilAvecNavigateurMisAJour_pasDAlerte_autreAppareil_alerte() {
        when(repository.findTop100ByUserIdAndEchecIsNullOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                LoginHistory.builder().deviceType(DeviceType.DESKTOP).browser("Chrome 140").os("Windows 10").build()));

        assertFalse(service.estNouvelAppareil(7L, client(DeviceType.DESKTOP, "Chrome 141", "Windows 11")));
        assertTrue(service.estNouvelAppareil(7L, client(DeviceType.MOBILE, "Chrome Mobile 141", "Android 14")));
        assertTrue(service.estNouvelAppareil(7L, client(DeviceType.DESKTOP, "Firefox 130", "Windows 10")));
    }

    @Test
    void decrireAppareil_navigateurNonReconnu_appareilNonIdentifie() {
        assertEquals("Chrome 141 sur Windows 10", LoginHistoryService.decrireAppareil("Chrome 141", "Windows 10"));
        assertEquals("Firefox 130", LoginHistoryService.decrireAppareil("Firefox 130", "Other"));
        assertEquals("un appareil non identifié", LoginHistoryService.decrireAppareil("Other", "Other"));
        assertEquals("un appareil non identifié", LoginHistoryService.decrireAppareil(null, null));
    }
}
