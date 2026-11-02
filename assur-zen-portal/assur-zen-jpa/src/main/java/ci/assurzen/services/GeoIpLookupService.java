package ci.assurzen.services;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Geolocalisation IP (ville/pays/ISP approxime) via bases locales MaxMind GeoLite2 (.mmdb),
 * volontairement tolerante : si les fichiers sont absents (cf. GeoIpDatabaseUpdateScheduler et le
 * prerequis compte MaxMind cote utilisateur), le service reste simplement inactif - aucune
 * connexion ne doit jamais echouer a cause d'une base GeoIP manquante ou d'une IP non trouvee.
 */
@Slf4j
@Service
public class GeoIpLookupService {

    @Value("${app.geoip.city-db-path:}")
    private String cityDbPath;

    @Value("${app.geoip.asn-db-path:}")
    private String asnDbPath;

    private volatile DatabaseReader cityReader;
    private volatile DatabaseReader asnReader;

    public record GeoIpResult(String country, String city, String isp) {
        static final GeoIpResult EMPTY = new GeoIpResult(null, null, null);
    }

    @PostConstruct
    void init() {
        cityReader = openReader(cityDbPath, "GeoLite2-City");
        asnReader = openReader(asnDbPath, "GeoLite2-ASN");
    }

    private DatabaseReader openReader(String path, String label) {
        if (!StringUtils.hasText(path)) {
            log.info("Base {} non configuree (app.geoip.*-db-path vide) - geolocalisation IP inactive pour cette base", label);
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            log.warn("Base {} introuvable au chemin '{}' - geolocalisation IP inactive pour cette base", label, path);
            return null;
        }
        try {
            return new DatabaseReader.Builder(file).withCache(new CHMCache()).build();
        } catch (IOException e) {
            log.warn("Echec d'ouverture de la base {} ('{}') : {}", label, path, e.getMessage());
            return null;
        }
    }

    /** Recharge appelee par GeoIpDatabaseUpdateScheduler apres remplacement des fichiers .mmdb. */
    synchronized void reload() {
        closeQuietly(cityReader);
        closeQuietly(asnReader);
        init();
    }

    public GeoIpResult lookup(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || isPrivateOrLoopback(ipAddress)) {
            return GeoIpResult.EMPTY;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            return GeoIpResult.EMPTY;
        }

        String country = null;
        String city = null;
        if (cityReader != null) {
            try {
                CityResponse response = cityReader.city(address);
                country = response.getCountry() != null ? response.getCountry().getName() : null;
                city = response.getCity() != null ? response.getCity().getName() : null;
            } catch (IOException | GeoIp2Exception e) {
                log.debug("Lookup GeoLite2-City sans resultat pour {} : {}", ipAddress, e.getMessage());
            }
        }

        String isp = null;
        if (asnReader != null) {
            try {
                AsnResponse response = asnReader.asn(address);
                isp = response.getAutonomousSystemOrganization();
            } catch (IOException | GeoIp2Exception e) {
                log.debug("Lookup GeoLite2-ASN sans resultat pour {} : {}", ipAddress, e.getMessage());
            }
        }

        return new GeoIpResult(country, city, isp);
    }

    private boolean isPrivateOrLoopback(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            // Adresse IPv6 non loopback (ou format inattendu) : pas de filtrage particulier ici,
            // le lookup GeoLite2 echouera simplement silencieusement si l'IP n'est pas trouvee.
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 127 || first == 10
                    || (first == 192 && second == 168)
                    || (first == 172 && second >= 16 && second <= 31);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void closeQuietly(DatabaseReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException e) {
            log.debug("Erreur non bloquante a la fermeture d'une base GeoIP : {}", e.getMessage());
        }
    }
}
