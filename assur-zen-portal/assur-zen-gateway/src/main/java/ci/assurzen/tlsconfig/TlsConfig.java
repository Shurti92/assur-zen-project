package ci.assurzen.tlsconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("tls")
@ConfigurationProperties(prefix = "app.tls")
public class TlsConfig {

    private boolean enabled = true;
    private String bundleName = "webserver";
    private String certificate;
    private String privateKey;
    private boolean reloadOnUpdate = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public boolean isReloadOnUpdate() {
        return reloadOnUpdate;
    }

    public void setReloadOnUpdate(boolean reloadOnUpdate) {
        this.reloadOnUpdate = reloadOnUpdate;
    }

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }

        if (!StringUtils.hasText(certificate) || !StringUtils.hasText(privateKey)) {
            throw new IllegalStateException(
                    "TLS profile is active but certificate/private-key are not configured."
            );
        }
    }
}
