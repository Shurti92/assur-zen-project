package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.DynamicConfigurationRequest;
import ci.assurzen.dto.DynamicConfigurationResponse;
import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.repositories.HistoriqueModificationRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garde-fous des configurations SENSIBLES (2026-09-24, identifiants SycaPay stockes en base) : un secret
 * ne doit jamais pouvoir etre stocke, affiche ou ecrase en clair, ni chiffre avec une cle publique.
 */
class DynamicConfigurationServiceSecuriteTest {

    private static final String SECRET = "cle-de-test-longue-de-plus-de-32-caracteres";
    private static final String SEL = "a1b2c3d4e5f60718";

    private final DynamicConfigurationRepository repository = mock(DynamicConfigurationRepository.class);
    private final ConfigurationEncryptionService encryption = new ConfigurationEncryptionService(SECRET, SEL);

    private DynamicConfigurationService service(ConfigurationEncryptionService encryptionService) {
        DynamicConfigurationService service = new DynamicConfigurationService(repository, mock(HistoriqueModificationRepository.class),
                mock(ConfigurationCacheService.class), encryptionService, mock(FunctionalAuditRecorder.class),
                mock(ApplicationEventPublisher.class));
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        ReflectionTestUtils.setField(service, "authService", authService);
        return service;
    }

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DynamicConfigurationRequest requete(String key, String value, Boolean sensitive, String defaultValue) {
        return new DynamicConfigurationRequest(key, value, ConfigValueType.STRING, "desc", "SYCAPAY", ConfigCategory.GENERAL,
                null, defaultValue, null, null, true, true, sensitive, false, true, 1, "test");
    }

    private DynamicConfiguration existante(String key, String storedValue, boolean sensitive) {
        DynamicConfiguration entity = DynamicConfiguration.builder().configKey(key).configValue(storedValue)
                .valueType(ConfigValueType.STRING).category(ConfigCategory.GENERAL).active(true).editable(true)
                .sensitive(sensitive).build();
        when(repository.findByConfigKey(key)).thenReturn(Optional.of(entity));
        return entity;
    }

    @Test
    void creation_cleSecrete_estToujoursSensible_etChiffree_memeSiLeFlagDitNon() {
        when(repository.findByConfigKey("SYCAPAY_SECRET_KEY")).thenReturn(Optional.empty());

        DynamicConfigurationResponse response = service(encryption).create(requete("SYCAPAY_SECRET_KEY", "sk_syca_test", false, null));

        ArgumentCaptor<DynamicConfiguration> saved = ArgumentCaptor.forClass(DynamicConfiguration.class);
        verify(repository).save(saved.capture());
        assertTrue(saved.getValue().isSensitive());
        assertFalse(saved.getValue().getConfigValue().contains("sk_syca"), "jamais stocke en clair");
        assertEquals("sk_syca_test", encryption.decrypt(saved.getValue().getConfigValue()));
        assertEquals(DynamicConfigurationService.MASQUE, response.configValue(), "jamais renvoye par l'API");
    }

    @Test
    void valeurSensible_refusee_siLaCleDeChiffrementNestPasSecurisee() {
        when(repository.findByConfigKey("SYCAPAY_API_KEY")).thenReturn(Optional.empty());
        DynamicConfigurationService serviceCleParDefaut = service(new ConfigurationEncryptionService("", ""));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceCleParDefaut.create(requete("SYCAPAY_API_KEY", "pk_syca_test", true, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void modification_masqueRenvoyeTelQuel_conserveLeSecretExistant() {
        String chiffreExistant = encryption.encrypt("sk_syca_ancien");
        existante("SYCAPAY_SECRET_KEY", chiffreExistant, true);

        service(encryption).update("SYCAPAY_SECRET_KEY", requete("SYCAPAY_SECRET_KEY", DynamicConfigurationService.MASQUE, true, null));

        ArgumentCaptor<DynamicConfiguration> saved = ArgumentCaptor.forClass(DynamicConfiguration.class);
        verify(repository).save(saved.capture());
        assertEquals(chiffreExistant, saved.getValue().getConfigValue());
    }

    @Test
    void modification_nouveauSecret_estChiffre() {
        existante("SYCAPAY_SECRET_KEY", encryption.encrypt("sk_syca_ancien"), true);

        service(encryption).update("SYCAPAY_SECRET_KEY", requete("SYCAPAY_SECRET_KEY", "sk_syca_nouveau", true, null));

        ArgumentCaptor<DynamicConfiguration> saved = ArgumentCaptor.forClass(DynamicConfiguration.class);
        verify(repository).save(saved.capture());
        assertEquals("sk_syca_nouveau", encryption.decrypt(saved.getValue().getConfigValue()));
    }

    @Test
    void modification_repasserEnNonSensible_estRefusee() {
        existante("SYCAPAY_MERCHANT_ID", encryption.encrypt("C_TEST"), true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service(encryption)
                .update("SYCAPAY_MERCHANT_ID", requete("SYCAPAY_MERCHANT_ID", "C_TEST", false, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    /** Une valeur par defaut n'est jamais chiffree : elle est interdite sur une configuration sensible. */
    @Test
    void valeurParDefaut_interditeSurUneConfigurationSensible() {
        existante("SYCAPAY_API_KEY", encryption.encrypt("pk_syca_test"), true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service(encryption)
                .update("SYCAPAY_API_KEY", requete("SYCAPAY_API_KEY", "pk_syca_nouveau", true, "pk_en_clair")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void lecture_serveur_dechiffre_maisLAPI_masque() {
        DynamicConfiguration entity = existante("SYCAPAY_API_KEY", encryption.encrypt("pk_syca_test"), true);
        DynamicConfigurationService service = service(encryption);

        assertEquals("pk_syca_test", service.getString("SYCAPAY_API_KEY", null));
        DynamicConfigurationResponse response = service.get("SYCAPAY_API_KEY");
        assertEquals(DynamicConfigurationService.MASQUE, response.configValue());
        assertNull(response.defaultValue());
        assertTrue(entity.isSensitive());
    }
}
