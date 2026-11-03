package ci.assurzen.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Port HTTP clair supplementaire, DEV UNIQUEMENT (2026-11-03) : le gateway reste en HTTPS sur son port habituel (portail
 * back-office) et ouvre en plus un port HTTP (app.http-port, variable SERVER_HTTP_PORT) pour le telephone de test de
 * l'application mobile (adb reverse), qui n'accepte pas le certificat auto-signe. Vide = aucun port HTTP (prod, recette).
 */
@Configuration
public class AdditionalHttpConnectorConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalHttpConnector(
            @Value("${app.http-port:}") String httpPort) {
        return factory -> {
            if (!StringUtils.hasText(httpPort)) {
                return;
            }
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setScheme("http");
            connector.setSecure(false);
            connector.setPort(Integer.parseInt(httpPort.trim()));
            factory.addAdditionalConnectors(connector);
        };
    }
}
