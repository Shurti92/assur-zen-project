package ci.assurzen.services;

import ci.assurzen.entities.DeviceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Parsing best-effort du header User-Agent (device/navigateur/OS) pour l'historique de connexion.
 * ua-parser (regexes.yaml) n'est pas exhaustif : les appareils non reconnus retombent sur
 * DeviceType.INCONNU plutot que de faire echouer l'enregistrement de la connexion.
 */
@Slf4j
@Service
public class UserAgentParserService {

    private final Parser parser = new Parser();

    public record ParsedUserAgent(DeviceType deviceType, String browser, String os) {
    }

    public ParsedUserAgent parse(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return new ParsedUserAgent(DeviceType.INCONNU, null, null);
        }
        try {
            Client client = parser.parse(userAgent);
            return new ParsedUserAgent(
                    classifyDevice(userAgent, client),
                    formatBrowser(client),
                    formatOs(client));
        } catch (RuntimeException e) {
            log.warn("Echec du parsing du User-Agent '{}' : {}", userAgent, e.getMessage());
            return new ParsedUserAgent(DeviceType.INCONNU, null, null);
        }
    }

    /**
     * ua-parser n'expose pas directement une categorie "mobile/tablette/desktop/bot" - on
     * l'approxime a partir de la famille d'appareil detectee et de quelques marqueurs bruts
     * connus. Heuristique volontairement simple, pas une detection exhaustive.
     */
    private DeviceType classifyDevice(String rawUserAgent, Client client) {
        String device = client.device != null && client.device.family != null ? client.device.family : "";
        String os = client.os != null && client.os.family != null ? client.os.family : "";
        String lowerUa = rawUserAgent.toLowerCase(java.util.Locale.ROOT);

        if (lowerUa.contains("bot") || lowerUa.contains("spider") || lowerUa.contains("crawler")
                || "Spider".equalsIgnoreCase(device)) {
            return DeviceType.BOT;
        }
        if ("iPad".equalsIgnoreCase(device) || (os.equalsIgnoreCase("Android") && lowerUa.contains("tablet"))) {
            return DeviceType.TABLET;
        }
        if ("iPhone".equalsIgnoreCase(device) || "iPod".equalsIgnoreCase(device)
                || (os.equalsIgnoreCase("Android") && lowerUa.contains("mobile"))
                || lowerUa.contains("mobi")) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }

    private String formatBrowser(Client client) {
        if (client.userAgent == null || client.userAgent.family == null) {
            return null;
        }
        String version = client.userAgent.major != null ? " " + client.userAgent.major : "";
        return client.userAgent.family + version;
    }

    private String formatOs(Client client) {
        if (client.os == null || client.os.family == null) {
            return null;
        }
        String version = client.os.major != null ? " " + client.os.major : "";
        return client.os.family + version;
    }
}
