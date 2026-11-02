package ci.assurzen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Notifications Telegram (canal choisi par le produit le 2026-08-23 : gratuit, illimite, pas de
 * fournisseur payant contrairement au SMS deja en place). Desactive par defaut (enabled=false) tant
 * que le bot n'est pas cree et le token/chatId non fournis - cf. TelegramNotificationService.
 */
@Configuration
@ConfigurationProperties(prefix = "credential.telegram")
@Getter
@Setter
public class TelegramProperties {

    private boolean enabled;
    private String botToken;
    private String chatId;
}
