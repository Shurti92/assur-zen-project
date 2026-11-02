package ci.assurzen.sycapay.config;

import ci.assurzen.services.ConfigurationEncryptionService;
import ci.assurzen.services.DynamicConfigurationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre le branchement sur le Dynamic Configuration Engine (2026-09-22) : reglages operationnels
 * modifiables depuis le back-office ; identifiants (merchantId/apiKey/secretKey) lus en base chiffree
 * uniquement avec une cle de chiffrement securisee, sinon depuis l'environnement (2026-09-24).
 */
class SycaPayPropertiesTest {

    @Test
    void sansDynamicConfigurationService_retombeSurLaValeurStatique() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(true);
        properties.setProvider("Wave");
        properties.setBalanceCacheTtlSeconds(99L);

        assertTrue(properties.isEnabled());
        assertEquals("Wave", properties.getProvider());
        assertEquals(99L, properties.getBalanceCacheTtlSeconds());
    }

    @Test
    void avecDynamicConfigurationService_prioriseLaValeurEnBase() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(false);
        properties.setProvider("Wave");

        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        when(dynamicConfigurationService.getBoolean(eq("SYCAPAY_ENABLED"), eq(false))).thenReturn(true);
        when(dynamicConfigurationService.getString(eq("SYCAPAY_PROVIDER"), eq("Wave"))).thenReturn("Orange");
        properties.setDynamicConfigurationService(dynamicConfigurationService);

        assertTrue(properties.isEnabled());
        assertEquals("Orange", properties.getProvider());
    }

    @Test
    void avecDynamicConfigurationServiceMaisAucuneLigneEnBase_retombeSurLaValeurStatique() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(true);
        properties.setCurrency("XOF");

        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        // Comportement reel de DynamicConfigurationService.getBoolean/getString(key, fallback) quand
        // aucune ligne n'existe encore en base pour cette cle : renvoie le fallback fourni.
        when(dynamicConfigurationService.getBoolean(eq("SYCAPAY_ENABLED"), eq(true))).thenReturn(true);
        when(dynamicConfigurationService.getString(eq("SYCAPAY_CURRENCY"), eq("XOF"))).thenReturn("XOF");
        properties.setDynamicConfigurationService(dynamicConfigurationService);

        assertTrue(properties.isEnabled());
        assertEquals("XOF", properties.getCurrency());
    }

    /** Sans cle de chiffrement securisee, la base n'est JAMAIS lue pour les identifiants (2026-09-24). */
    @Test
    void identifiants_sansCleSecurisee_viennentDeLEnvironnement_sansLireLaBase() {
        SycaPayProperties properties = proprietesAvecEnvironnement();
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        properties.setDynamicConfigurationService(dynamicConfigurationService);
        properties.setEncryptionService(new ConfigurationEncryptionService("", ""));

        assertEquals("C_ENV", properties.getMerchantId());
        assertEquals("API_ENV", properties.getApiKey());
        assertEquals("SECRET_ENV", properties.getSecretKey());
        org.mockito.Mockito.verifyNoInteractions(dynamicConfigurationService);
    }

    /** Cle securisee : la valeur en base (dechiffree par le moteur) est prioritaire, sans redemarrage. */
    @Test
    void identifiants_avecCleSecurisee_valeurEnBasePrioritaire() {
        SycaPayProperties properties = proprietesAvecEnvironnement();
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        when(dynamicConfigurationService.getString("SYCAPAY_MERCHANT_ID", null)).thenReturn("C_BASE");
        when(dynamicConfigurationService.getString("SYCAPAY_API_KEY", null)).thenReturn("API_BASE");
        when(dynamicConfigurationService.getString("SYCAPAY_SECRET_KEY", null)).thenReturn("SECRET_BASE");
        properties.setDynamicConfigurationService(dynamicConfigurationService);
        properties.setEncryptionService(cleSecurisee());

        assertEquals("C_BASE", properties.getMerchantId());
        assertEquals("API_BASE", properties.getApiKey());
        assertEquals("SECRET_BASE", properties.getSecretKey());
    }

    /** Ligne absente/vide ou illisible (mauvaise cle) : repli sur l'environnement, jamais d'identifiant vide. */
    @Test
    void identifiants_ligneVideOuIllisible_repliSurLEnvironnement() {
        SycaPayProperties properties = proprietesAvecEnvironnement();
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        when(dynamicConfigurationService.getString("SYCAPAY_MERCHANT_ID", null)).thenReturn(null);
        when(dynamicConfigurationService.getString("SYCAPAY_API_KEY", null)).thenReturn("  ");
        when(dynamicConfigurationService.getString("SYCAPAY_SECRET_KEY", null))
                .thenThrow(new IllegalStateException("Valeur de configuration sensible indechiffrable avec la cle configuree"));
        properties.setDynamicConfigurationService(dynamicConfigurationService);
        properties.setEncryptionService(cleSecurisee());

        assertEquals("C_ENV", properties.getMerchantId());
        assertEquals("API_ENV", properties.getApiKey());
        assertEquals("SECRET_ENV", properties.getSecretKey());
    }

    private static SycaPayProperties proprietesAvecEnvironnement() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setMerchantId("C_ENV");
        properties.setApiKey("API_ENV");
        properties.setSecretKey("SECRET_ENV");
        return properties;
    }

    private static ConfigurationEncryptionService cleSecurisee() {
        return new ConfigurationEncryptionService("cle-de-test-longue-de-plus-de-32-caracteres", "a1b2c3d4e5f60718");
    }

    @Test
    void isEnabled_false_neLevePasAuPostConstruct() {
        SycaPayProperties properties = new SycaPayProperties();
        assertFalse(properties.isEnabled());
    }
}
