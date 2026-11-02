package ci.assurzen.services;

import ci.assurzen.dto.MoEnvoiMessage;
import ci.assurzen.dto.SmsSendResult;
import ci.assurzen.entities.NotificateurSms;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.exception.SmsSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderService {

    private final SmsSenderService smsSenderService;
    private final DynamicConfigurationService dynamicConfigurationService;

    /**
     * Chokepoint des envois NotificateurSms (echeances, relances, suivis...) : si SMS_ENABLED=false
     * (Dynamic Configuration Engine), n'appelle pas le fournisseur et rend la main sans erreur.
     * Exclusion volontaire de otp_verification : couper SMS_ENABLED ne doit jamais empecher un
     * utilisateur de recevoir son code de connexion 2FA (decision explicite, cf memoire de session).
     */
    public String send(NotificateurSms sms) {
        if (sms.getTypeMessage() != TypeMessageSms.otp_verification
                && !dynamicConfigurationService.getBoolean("SMS_ENABLED", true)) {
            log.info("Envoi SMS ignore (SMS_ENABLED=false), type={}", sms.getTypeMessage());
            return null;
        }

        SmsSendResult result = smsSenderService.envoiMessageAuNumero(
                new MoEnvoiMessage(sms.getTelephoneAssure(), sms.getMessage())
        );

        if (!result.success()) {
            throw new SmsSendException("Echec de l'envoi SMS, statut HTTP " + result.httpStatus(), null);
        }

        return result.body();
    }
}
