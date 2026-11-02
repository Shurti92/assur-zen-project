package ci.assurzen.services;

import ci.assurzen.entities.NotificateurSms;
import ci.assurzen.entities.StatutEnvoiSms;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.NotificateurSmsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificateurSmsScheduler {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");

    private final NotificateurSmsRepository repository;
    private final ProviderService providerService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void envoyerAvisEcheance() {
        envoyerMessages(TypeMessageSms.avis_echeance);
    }



    @Scheduled(cron = "0 0 20 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void envoyerFinContrat() {
        envoyerMessages(TypeMessageSms.fin_contrat);
    }


    @Scheduled(cron = "0 0 14 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void envoyerRelanceEcheancePaiement() {
        envoyerMessages(TypeMessageSms.relance_echeance_paiement);
    }


    private void envoyerMessages(TypeMessageSms typeMessage) {
        LocalDate dateDuJour = LocalDate.now(ZONE_ID);
        List<NotificateurSms> messages = repository.findByDateEnvoiLessThanEqualAndTypeMessageAndStatutEnvoi(
                dateDuJour,
                typeMessage,
                StatutEnvoiSms.en_attente
        );

        log.info("{} SMS {} a envoyer pour le {}", messages.size(), typeMessage, dateDuJour);

        messages.forEach(sms -> {
            if (sms.getDateEnvoi().isBefore(dateDuJour)) {
                log.warn("SMS {} anti-date (date_envoi={}, aujourd'hui={}), envoi annule", sms.getId(), sms.getDateEnvoi(), dateDuJour);
                sms.setStatutEnvoi(StatutEnvoiSms.message_anti_date);
            } else {
                envoyerMessage(sms);
            }
        });
    }


    private void envoyerMessage(NotificateurSms sms) {
        try {
            String responseProvider = providerService.send(sms);
            sms.setResponseProvider(responseProvider);
            sms.setStatutEnvoi(StatutEnvoiSms.envoye);
        } catch (Exception exception) {
            log.error("Erreur lors de l'envoi du SMS {}", sms.getId(), exception);
            sms.setResponseProvider(exception.getMessage());
            sms.setStatutEnvoi(StatutEnvoiSms.non_envoye);
        }
    }
}
