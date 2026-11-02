package ci.assurzen.webpush.config;

import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.ConfigurationCacheService;
import ci.assurzen.services.ConfigurationEncryptionService;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.webpush.VapidTestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cles VAPID automatiques par environnement (2026-09-26) : environnement prioritaire, sinon base,
 * sinon generation au premier demarrage - jamais de cle privee en clair, jamais de regeneration. */
class VapidKeyInitializerTest {

    private final DynamicConfigurationService dynamicConfiguration = mock(DynamicConfigurationService.class);
    private final DynamicConfigurationRepository repository = mock(DynamicConfigurationRepository.class);
    private final ConfigurationEncryptionService encryption = mock(ConfigurationEncryptionService.class);
    private final ConfigurationCacheService cache = mock(ConfigurationCacheService.class);
    private WebPushProperties properties;
    private VapidKeyInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new WebPushProperties();
        properties.getVapid().setSubject("https://assurzen.ci");
        initializer = new VapidKeyInitializer(properties, dynamicConfiguration, repository, encryption, cache,
                mock(PlatformTransactionManager.class));
        when(encryption.isCleSecurisee()).thenReturn(true);
        when(encryption.encrypt(anyString())).thenAnswer(inv -> "chiffre:" + inv.getArgument(0).hashCode());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void premierDemarrage_paireGenereeEnregistreeAvecClePriveeChiffreeEtChargee() {
        initializer.initialiser();

        assertTrue(properties.isConfigured());
        ArgumentCaptor<DynamicConfiguration> lignes = ArgumentCaptor.forClass(DynamicConfiguration.class);
        verify(repository, times(2)).saveAndFlush(lignes.capture());
        DynamicConfiguration publique = lignes.getAllValues().get(0);
        DynamicConfiguration privee = lignes.getAllValues().get(1);
        assertEquals("WEBPUSH_VAPID_PUBLIC_KEY", publique.getConfigKey());
        assertFalse(publique.isSensitive());
        assertEquals(properties.keyPair().extractPublicKeyInUncompressedFormAsString(), publique.getConfigValue());
        assertEquals("WEBPUSH_VAPID_PRIVATE_KEY", privee.getConfigKey());
        assertTrue(privee.isSensitive());
        assertFalse(privee.isEditable());
        assertTrue(privee.getConfigValue().startsWith("chiffre:"));
    }

    @Test
    void demarragesSuivants_paireRelueEnBaseSansRegeneration() {
        VapidTestKeys.Paire paire = VapidTestKeys.nouvellePaire();
        when(dynamicConfiguration.getString(eq("WEBPUSH_VAPID_PUBLIC_KEY"), any())).thenReturn(paire.publique());
        when(dynamicConfiguration.getString(eq("WEBPUSH_VAPID_PRIVATE_KEY"), any())).thenReturn(paire.privee());

        initializer.initialiser();

        assertTrue(properties.isConfigured());
        assertEquals(paire.publique(), properties.keyPair().extractPublicKeyInUncompressedFormAsString());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void clesDansLEnvironnement_prioritaires_baseJamaisConsultee() {
        VapidTestKeys.Paire paire = VapidTestKeys.nouvellePaire();
        properties.getVapid().setPublicKey(paire.publique());
        properties.getVapid().setPrivateKey(paire.privee());
        properties.init();

        initializer.initialiser();

        assertTrue(properties.isConfigured());
        verify(dynamicConfiguration, never()).getString(anyString(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void sansCleDeChiffrementSecurisee_rienNestGenere_pushDesactive() {
        when(encryption.isCleSecurisee()).thenReturn(false);

        initializer.initialiser();

        assertFalse(properties.isConfigured());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void paireIncompleteEnBase_jamaisEcrasee_pushDesactive() {
        when(dynamicConfiguration.getString(eq("WEBPUSH_VAPID_PUBLIC_KEY"), any())).thenReturn(VapidTestKeys.nouvellePaire().publique());

        initializer.initialiser();

        assertFalse(properties.isConfigured());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creationConcurrente_paireDeLAutreInstanceRelueSansBoucle() {
        VapidTestKeys.Paire paireAutreInstance = VapidTestKeys.nouvellePaire();
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_dynamic_configuration_key"));
        when(dynamicConfiguration.getString(eq("WEBPUSH_VAPID_PUBLIC_KEY"), any())).thenReturn(null, paireAutreInstance.publique());
        when(dynamicConfiguration.getString(eq("WEBPUSH_VAPID_PRIVATE_KEY"), any())).thenReturn(null, paireAutreInstance.privee());

        initializer.initialiser();

        assertTrue(properties.isConfigured());
        assertEquals(paireAutreInstance.publique(), properties.keyPair().extractPublicKeyInUncompressedFormAsString());
        verify(cache).evict("WEBPUSH_VAPID_PUBLIC_KEY");
    }

    @Test
    void paireGeneree_formatNpmWebPush_etCleSecreteMasqueeDansToString() {
        VapidKeyGenerator.Paire paire = VapidKeyGenerator.generer();

        assertEquals(87, paire.publique().length());
        assertEquals(43, paire.privee().length());
        assertFalse(paire.toString().contains(paire.privee()));
        assertNotEquals(paire.publique(), VapidKeyGenerator.generer().publique());
        assertTrue(List.of(paire.publique().split("")).stream().noneMatch(c -> c.equals("=") || c.equals("+") || c.equals("/")));
    }
}
