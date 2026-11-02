package ci.assurzen.sycapay.service;

import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.events.ConfigurationChangedEvent;
import ci.assurzen.events.ConfigurationDeactivatedEvent;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.TelegramNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Changement d'un identifiant SycaPay en base : pris en compte immediatement + alerte de securite. */
class SycaPayIdentifiantsListenerTest {

    private final SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
    private final SycaPayBalanceService balanceService = mock(SycaPayBalanceService.class);
    private final DynamicConfigurationRepository repository = mock(DynamicConfigurationRepository.class);
    private final TelegramNotificationService telegram = mock(TelegramNotificationService.class);
    private final SycaPayIdentifiantsListener listener =
            new SycaPayIdentifiantsListener(authenticationService, balanceService, repository, telegram);

    @Test
    void identifiantModifie_invalideJetonEtSolde_etAlerteSansJamaisLaValeur() {
        DynamicConfiguration config = DynamicConfiguration.builder().configKey("SYCAPAY_SECRET_KEY").build();
        config.setUpdatedBy("admin.finance");
        when(repository.findByConfigKey("SYCAPAY_SECRET_KEY")).thenReturn(Optional.of(config));

        listener.onChanged(new ConfigurationChangedEvent("SYCAPAY_SECRET_KEY", "***", "***"));

        verify(authenticationService).invalidate();
        verify(balanceService).viderCache();
        ArgumentCaptor<String> alerte = ArgumentCaptor.forClass(String.class);
        verify(telegram).envoyer(alerte.capture());
        assertTrue(alerte.getValue().contains("SYCAPAY_SECRET_KEY") && alerte.getValue().contains("admin.finance"));
        assertFalse(alerte.getValue().contains("***"));
    }

    @Test
    void desactivation_estAussiSignalee() {
        listener.onDeactivated(new ConfigurationDeactivatedEvent("SYCAPAY_API_KEY"));

        verify(authenticationService).invalidate();
        verify(telegram).envoyer(any());
    }

    @Test
    void autresCles_ignorees() {
        listener.onChanged(new ConfigurationChangedEvent("SYCAPAY_PROVIDER", "Wave", "Orange"));

        verify(authenticationService, never()).invalidate();
        verify(telegram, never()).envoyer(any());
    }
}
