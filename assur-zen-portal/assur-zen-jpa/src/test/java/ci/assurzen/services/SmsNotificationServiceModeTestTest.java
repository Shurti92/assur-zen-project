package ci.assurzen.services;

import ci.assurzen.entities.CommunicationTemplate;
import ci.assurzen.entities.NotificateurSms;
import ci.assurzen.entities.TemplateStatut;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.CommunicationTemplateRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.NotificateurSmsRepository;
import ci.assurzen.sycapay.config.SycaPayModeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mode test SycaPay (hors production, 2026-09-24) : les SMS de commission partent au numero de test,
 * texte reel prefixe du vrai destinataire ; les SMS d'authentification ne sont JAMAIS rediriges.
 */
class SmsNotificationServiceModeTestTest {

    private final ProviderService providerService = mock(ProviderService.class);
    private final CommunicationTemplateRepository templateRepository = mock(CommunicationTemplateRepository.class);
    private final TemplateVariableResolver resolver = mock(TemplateVariableResolver.class);
    private final SmsNotificationService service = new SmsNotificationService(providerService, mock(NotificateurSmsRepository.class),
            mock(ContractRepository.class), templateRepository, resolver);

    @BeforeEach
    void setUp() {
        when(providerService.send(any())).thenReturn("OK");
        CommunicationTemplate template = mock(CommunicationTemplate.class);
        when(template.getStatut()).thenReturn(TemplateStatut.ACTIF);
        when(template.isActive()).thenReturn(true);
        when(template.getContenu()).thenReturn("gabarit");
        when(templateRepository.findByCode(anyString())).thenReturn(Optional.of(template));
        when(resolver.resoudreAvecValeurs(any(), any())).thenReturn("AssurZen : votre commission de 25 000 FCFA a ete versee.");
    }

    private NotificateurSms smsEnvoye() {
        ArgumentCaptor<NotificateurSms> sms = ArgumentCaptor.forClass(NotificateurSms.class);
        verify(providerService).send(sms.capture());
        return sms.getValue();
    }

    @Test
    void modeTestActif_smsDeCommission_redirigeAvecLeVraiDestinataireEnPrefixe() {
        ReflectionTestUtils.setField(service, "modeTest", new SycaPayModeTest(true, "0757000099", 100L, new String[]{"dev"}));

        boolean envoye = service.sendFromTemplate("0701020304", "COMMISSION_PAYEE", Map.of(), TypeMessageSms.commission_payee);

        assertTrue(envoye);
        NotificateurSms sms = smsEnvoye();
        assertEquals("0757000099", sms.getTelephoneAssure());
        assertEquals("[TEST -> 0701020304] AssurZen : votre commission de 25 000 FCFA a ete versee.", sms.getMessage());
    }

    @Test
    void modeTestActif_smsDAuthentification_jamaisRedirige() {
        ReflectionTestUtils.setField(service, "modeTest", new SycaPayModeTest(true, "0757000099", 100L, new String[]{"dev"}));

        service.sendOtpSms("0701020304", "123456", 5);

        NotificateurSms sms = smsEnvoye();
        assertEquals("0701020304", sms.getTelephoneAssure());
        assertTrue(!sms.getMessage().startsWith("[TEST"));
    }

    @Test
    void modeTestInactif_rienNestRedirige() {
        ReflectionTestUtils.setField(service, "modeTest", new SycaPayModeTest(false, "0757000099", 100L, new String[]{"dev"}));

        service.sendFromTemplate("0701020304", "COMMISSION_PAYEE", Map.of(), TypeMessageSms.commission_payee);

        assertEquals("0701020304", smsEnvoye().getTelephoneAssure());
    }

    @Test
    void enProduction_rienNestRedirige_memeSiDemande() {
        ReflectionTestUtils.setField(service, "modeTest", new SycaPayModeTest(true, "0757000099", 100L, new String[]{"prod"}));

        service.sendFromTemplate("0701020304", "COMMISSION_PAYEE", Map.of(), TypeMessageSms.commission_payee);

        assertEquals("0701020304", smsEnvoye().getTelephoneAssure());
    }
}
