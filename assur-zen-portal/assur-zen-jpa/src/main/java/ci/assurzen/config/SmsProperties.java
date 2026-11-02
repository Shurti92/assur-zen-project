package ci.assurzen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "credential.sms")
@Getter
@Setter
public class SmsProperties {

    private String bearer;
    private String sender;
    private String baseUrl;
    /** Dev/test uniquement : journalise le SMS sans appeler le fournisseur (SMS_MOCK=true). */
    private boolean mock = false;
}
