package ci.assurzen.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * Premier usage reel de spring-boot-starter-mail dans ce projet (jusqu'ici la dependance existait
 * sans JavaMailSender configure) : necessaire pour l'envoi des campagnes marketing par email
 * (module Fidelisation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final DynamicConfigurationService dynamicConfigurationService;

    @Value("${mailer.from.address}")
    private String fromAddress;

    @Value("${mailer.from.name}")
    private String fromName;

    /** Chokepoint : EMAIL_ENABLED=false (Dynamic Configuration Engine) coupe tout envoi d'email. */
    public boolean envoyer(String destinataire, String sujet, String corpsHtml) {
        if (!dynamicConfigurationService.getBoolean("EMAIL_ENABLED", true)) {
            log.info("Envoi email ignore (EMAIL_ENABLED=false) pour {}", destinataire);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(destinataire);
            helper.setSubject(sujet);
            helper.setText(corpsHtml, true);
            mailSender.send(message);
            return true;
        } catch (MailException | UnsupportedEncodingException | MessagingException e) {
            log.warn("Echec de l'envoi email a {}: {}", destinataire, e.getMessage());
            return false;
        }
    }
}
