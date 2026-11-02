package ci.assurzen.services;

import ci.assurzen.dto.NotificationPreferenceDto;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.NotificationCategorie;
import ci.assurzen.entities.NotificationPreference;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.Profile;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.NotificationPreferenceRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Preferences de notification par categorie (2026-10-03). */
class NotificationPreferenceServiceTest {

    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final CommercialRepository commercialRepository = mock(CommercialRepository.class);
    private final NotificationPreferenceService service = new NotificationPreferenceService(repository, authService,
            profileRepository, partenaireRepository, commercialRepository);

    private void connecterPartenaire() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(7L, "partenaire", "USER"));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(Profile.builder().id(70L).build()));
        when(partenaireRepository.findByProfile_Id(70L)).thenReturn(List.of(Partenaire.builder().id(9L).build()));
    }

    private void connecterCommercial() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(8L, "commercial", "COMMERCIAL"));
        when(profileRepository.findByUserId(8L)).thenReturn(Optional.of(Profile.builder().id(80L).build()));
        when(partenaireRepository.findByProfile_Id(80L)).thenReturn(List.of());
        when(commercialRepository.findByProfile_UserId(8L)).thenReturn(Optional.of(Commercial.builder().id(3L).build()));
    }

    @Test
    void partenaire_categoriesEmises_toutActiveParDefaut_smsVerseNonModifiable() {
        connecterPartenaire();

        List<NotificationPreferenceDto> prefs = service.mesPreferences();

        assertEquals(List.of(NotificationCategorie.COMMISSION_GAGNEE, NotificationCategorie.COMMISSION_VERSEE,
                        NotificationCategorie.ECHEANCE, NotificationCategorie.PRIME_A_REVERSER, NotificationCategorie.DOCUMENT,
                        NotificationCategorie.SECURITE),
                prefs.stream().map(NotificationPreferenceDto::categorie).toList());
        assertTrue(prefs.stream().allMatch(NotificationPreferenceDto::push));
        assertTrue(prefs.get(0).sms() && prefs.get(0).smsModifiable());
        assertTrue(prefs.get(1).sms());
        assertFalse(prefs.get(1).smsModifiable());
        // Nouvelles categories : jamais de SMS ; securite : push non desactivable
        assertFalse(prefs.get(2).sms());
        assertFalse(prefs.get(5).pushModifiable());
        assertFalse(prefs.get(5).modifiable());
    }

    @Test
    void commercial_argentVerseEtBonus() {
        connecterCommercial();

        assertEquals(List.of(NotificationCategorie.COMMISSION_VERSEE, NotificationCategorie.BONUS, NotificationCategorie.SECURITE),
                service.mesPreferences().stream().map(NotificationPreferenceDto::categorie).toList());
    }

    @Test
    void modifier_smsDeLArgentVerseResteActive() {
        connecterPartenaire();
        when(repository.findByUserIdAndCategorie(7L, NotificationCategorie.COMMISSION_VERSEE)).thenReturn(Optional.empty());

        service.modifierMesPreferences(List.of(new NotificationPreferenceDto(NotificationCategorie.COMMISSION_VERSEE,
                false, false, null, null, null)));

        ArgumentCaptor<NotificationPreference> saved = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(repository).save(saved.capture());
        assertFalse(saved.getValue().isPush());
        assertTrue(saved.getValue().isSms());
    }

    @Test
    void modifier_categorieNonRecueParLeCompte_400() {
        connecterPartenaire();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.modifierMesPreferences(
                List.of(new NotificationPreferenceDto(NotificationCategorie.BONUS, false, false, null, null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void autorisations_selonPreferenceEtRegles() {
        when(repository.findByUserIdAndCategorie(7L, NotificationCategorie.COMMISSION_GAGNEE)).thenReturn(Optional.of(
                NotificationPreference.builder().userId(7L).categorie(NotificationCategorie.COMMISSION_GAGNEE).push(false).sms(false).build()));

        assertFalse(service.pushAutorise(7L, PartenaireNotificationType.COMMISSION_DISPONIBLE));
        assertFalse(service.smsAutorise(7L, PartenaireNotificationType.COMMISSION_GAGNEE));
        assertTrue(service.smsAutorise(7L, PartenaireNotificationType.COMMISSION_VERSEE));
        assertTrue(service.pushAutorise(null, PartenaireNotificationType.COMMISSION_GAGNEE));
    }

    @Test
    void securite_pushToujoursAutorise_memeCoupeEnBase() {
        when(repository.findByUserIdAndCategorie(7L, NotificationCategorie.SECURITE)).thenReturn(Optional.of(
                NotificationPreference.builder().userId(7L).categorie(NotificationCategorie.SECURITE).push(false).build()));

        assertTrue(service.pushAutorise(7L, PartenaireNotificationType.NOUVELLE_CONNEXION));
    }
}
