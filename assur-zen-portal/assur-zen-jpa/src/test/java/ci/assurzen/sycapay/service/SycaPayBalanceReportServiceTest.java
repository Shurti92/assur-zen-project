package ci.assurzen.sycapay.service;

import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.TelegramNotificationService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Solde SycaPay sur Telegram (2026-09-26) : rapport du matin, alerte de seuil bas, envoi a la demande. */
class SycaPayBalanceReportServiceTest {

    private final SycaPayClient client = mock(SycaPayClient.class);
    private final SycaPayProperties properties = mock(SycaPayProperties.class);
    private final TelegramNotificationService telegram = mock(TelegramNotificationService.class);
    private final DynamicConfigurationService configuration = mock(DynamicConfigurationService.class);
    private final SycaPayBalanceReportService service = new SycaPayBalanceReportService(client, properties, telegram, configuration);

    @BeforeEach
    void setUp() {
        when(properties.getMerchantId()).thenReturn("MARCHAND");
        when(configuration.getBoolean(eq("SYCAPAY_BALANCE_REPORT_ENABLED"), any())).thenReturn(true);
        when(configuration.getLong(eq("SYCAPAY_BALANCE_ALERT_THRESHOLD"), any())).thenReturn(50_000L);
    }

    private void solde(String wave) {
        when(client.checkBalance()).thenReturn(new SycaPayBalanceResponse("checkcashoutsolde", "OK", List.of(
                new SycaPayBalanceEntry("1", wave, "Wave", "CI", "MARCHAND"),
                new SycaPayBalanceEntry("2", "0.00", "Orange", "CI", "MARCHAND"))));
    }

    private String messageEnvoye() {
        ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
        verify(telegram).envoyer(texte.capture());
        return texte.getValue();
    }

    @Test
    void rapportMatinal_soldeParOperateurEtEtatDesVirements() {
        solde("1234567.50");

        service.rapportMatinal();

        String texte = messageEnvoye();
        assertTrue(texte.contains("Solde SycaPay - debut de journee"));
        assertTrue(texte.contains("Wave : <b>1 234 568 FCFA</b>"));
        assertTrue(texte.contains("Orange : <b>0 FCFA</b>"));
        assertTrue(texte.contains("Virements de commission : desactives"));
        assertFalse(texte.contains("sous le seuil"));
    }

    @Test
    void rapportMatinal_soldeSousLeSeuil_signale() {
        solde("12000.00");

        service.rapportMatinal();

        assertTrue(messageEnvoye().contains("Attention : solde Wave sous le seuil d'alerte (50 000 FCFA)"));
    }

    @Test
    void soldeIllisible_signaleSurTelegramPlutotQueLeSilence() {
        when(client.checkBalance()).thenThrow(new SycaPayBalanceException("timeout"));

        service.rapportMatinal();

        assertTrue(messageEnvoye().contains("Solde illisible"));
    }

    @Test
    void surveillanceDuSeuil_uneSeuleAlerteParJour() {
        solde("12000.00");

        service.surveillerSeuil();
        service.surveillerSeuil();

        verify(telegram, times(1)).envoyer(anyString());
        assertTrue(messageEnvoye().contains("Alerte - solde SycaPay bas"));
    }

    @Test
    void surveillanceDuSeuil_soldeSuffisant_aucuneAlerte() {
        solde("75000.00");

        service.surveillerSeuil();

        verify(telegram, never()).envoyer(anyString());
    }

    @Test
    void seuilReglable_depuisLaConfiguration() {
        solde("75000.00");
        when(configuration.getLong(eq("SYCAPAY_BALANCE_ALERT_THRESHOLD"), any())).thenReturn(100_000L);

        service.surveillerSeuil();

        assertTrue(messageEnvoye().contains("sous le seuil de 100 000 FCFA"));
    }

    @Test
    void rapportDesactive_rienNestEnvoye() {
        when(configuration.getBoolean(eq("SYCAPAY_BALANCE_REPORT_ENABLED"), any())).thenReturn(false);

        service.rapportMatinal();
        service.surveillerSeuil();

        verify(telegram, never()).envoyer(anyString());
        verify(client, never()).checkBalance();
    }

    @Test
    void formatDesMontants() {
        assertEquals("0", SycaPayBalanceReportService.formater(BigDecimal.ZERO));
        assertEquals("950", SycaPayBalanceReportService.formater(new BigDecimal("950")));
        assertEquals("50 000", SycaPayBalanceReportService.formater(new BigDecimal("50000")));
        assertEquals("1 234 568", SycaPayBalanceReportService.formater(new BigDecimal("1234567.5")));
    }
}
