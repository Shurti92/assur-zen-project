package ci.assurzen.services;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GeoIpLookupService doit rester totalement tolerant : aucune IP, aucune base absente/corrompue ne
 * doit jamais faire echouer un login (cf. javadoc de la classe). Ces tests couvrent ce contrat, plus
 * la logique de detection des IP privees/loopback (bug potentiel aux bornes des plages RFC 1918).
 * Aucune vraie base .mmdb n'est embarquee dans le repo (fichiers proprietaires MaxMind, ~75 Mo) : la
 * resolution reelle (pays/ville/ISP) a ete verifiee manuellement en direct, cf. memoire de session.
 */
class GeoIpLookupServiceTest {

    @Test
    void lookup_adresseNulleOuVide_retourneResultatVideSansErreur() {
        GeoIpLookupService service = new GeoIpLookupService();

        assertNull(service.lookup(null).country());
        assertNull(service.lookup("").country());
        assertNull(service.lookup("   ").country());
    }

    @Test
    void lookup_adressesLoopback_retourneResultatVideSansInterrogerLesBases() throws Exception {
        GeoIpLookupService service = new GeoIpLookupService();
        DatabaseReader cityReader = mock(DatabaseReader.class);
        DatabaseReader asnReader = mock(DatabaseReader.class);
        ReflectionTestUtils.setField(service, "cityReader", cityReader);
        ReflectionTestUtils.setField(service, "asnReader", asnReader);

        assertNull(service.lookup("127.0.0.1").country());
        assertNull(service.lookup("::1").country());
        assertNull(service.lookup("0:0:0:0:0:0:0:1").country());

        verify(cityReader, never()).city(org.mockito.ArgumentMatchers.any());
        verify(asnReader, never()).asn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lookup_adressesPriveesRfc1918_retourneResultatVideSansInterrogerLesBases() throws Exception {
        GeoIpLookupService service = new GeoIpLookupService();
        DatabaseReader cityReader = mock(DatabaseReader.class);
        DatabaseReader asnReader = mock(DatabaseReader.class);
        ReflectionTestUtils.setField(service, "cityReader", cityReader);
        ReflectionTestUtils.setField(service, "asnReader", asnReader);

        for (String ip : new String[] { "10.0.0.5", "192.168.1.1", "172.16.0.1", "172.31.255.255" }) {
            assertNull(service.lookup(ip).country(), "IP privee attendue filtree : " + ip);
        }

        verify(cityReader, never()).city(org.mockito.ArgumentMatchers.any());
        verify(asnReader, never()).asn(org.mockito.ArgumentMatchers.any());
    }

    /** Regression bornes : 172.15.x et 172.32.x sont HORS de la plage privee 172.16.0.0/12 - ne
     * doivent PAS etre filtrees, contrairement a 172.16.x/172.31.x testes ci-dessus. */
    @Test
    void lookup_adressesAuxBornesDeLaPlage172_neSontPasFausenementFiltrees() throws Exception {
        GeoIpLookupService service = new GeoIpLookupService();
        DatabaseReader cityReader = mock(DatabaseReader.class);
        DatabaseReader asnReader = mock(DatabaseReader.class);
        ReflectionTestUtils.setField(service, "cityReader", cityReader);
        ReflectionTestUtils.setField(service, "asnReader", asnReader);
        when(cityReader.city(org.mockito.ArgumentMatchers.any())).thenReturn(mock(CityResponse.class));
        when(asnReader.asn(org.mockito.ArgumentMatchers.any())).thenReturn(mock(AsnResponse.class));

        service.lookup("172.15.255.255");
        service.lookup("172.32.0.1");

        verify(cityReader, org.mockito.Mockito.times(2)).city(org.mockito.ArgumentMatchers.any());
        verify(asnReader, org.mockito.Mockito.times(2)).asn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lookup_ipv6NonLoopback_neLeveJamaisMemeSansBaseConfiguree() {
        GeoIpLookupService service = new GeoIpLookupService();

        assertDoesNotThrow(() -> service.lookup("2001:4860:4860::8888"));
    }

    @Test
    void init_cheminsVides_neConfigureAucuneBaseEtNeLeveJamais() {
        GeoIpLookupService service = new GeoIpLookupService();
        ReflectionTestUtils.setField(service, "cityDbPath", "");
        ReflectionTestUtils.setField(service, "asnDbPath", "");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "init"));
        assertNull(ReflectionTestUtils.getField(service, "cityReader"));
        assertNull(ReflectionTestUtils.getField(service, "asnReader"));
        assertNull(service.lookup("8.8.8.8").country());
    }

    @Test
    void init_cheminConfigureMaisFichierInexistant_neConfigureAucuneBaseEtNeLeveJamais() {
        GeoIpLookupService service = new GeoIpLookupService();
        ReflectionTestUtils.setField(service, "cityDbPath", "chemin/qui/nexiste/pas.mmdb");
        ReflectionTestUtils.setField(service, "asnDbPath", "chemin/qui/nexiste/pas/non-plus.mmdb");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "init"));
        assertNull(ReflectionTestUtils.getField(service, "cityReader"));
        assertNull(ReflectionTestUtils.getField(service, "asnReader"));
    }

    @Test
    void init_fichierPresentMaisCorrompu_neConfigureAucuneBaseEtNeLeveJamais() throws IOException {
        Path fichierCorrompu = Files.createTempFile("geoip-test-corrompu", ".mmdb");
        Files.writeString(fichierCorrompu, "ceci-nest-pas-une-base-mmdb-valide");
        try {
            GeoIpLookupService service = new GeoIpLookupService();
            ReflectionTestUtils.setField(service, "cityDbPath", fichierCorrompu.toString());
            ReflectionTestUtils.setField(service, "asnDbPath", "");

            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "init"));
            assertNull(ReflectionTestUtils.getField(service, "cityReader"));
        } finally {
            // Sous Windows, un fichier ouvert via memory-mapping (meme apres echec d'ouverture cote
            // MaxMind) peut rester verrouille jusqu'au passage du GC - suppression au mieux, sans
            // faire echouer le test la-dessus (hors du comportement teste ici).
            fichierCorrompu.toFile().deleteOnExit();
            try {
                Files.deleteIfExists(fichierCorrompu);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void reload_sansAucuneBaseOuverteAuPrealable_neLeveJamais() {
        GeoIpLookupService service = new GeoIpLookupService();
        ReflectionTestUtils.setField(service, "cityDbPath", "");
        ReflectionTestUtils.setField(service, "asnDbPath", "");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "reload"));
    }
}
