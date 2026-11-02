package ci.assurzen.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Telecharge mensuellement les dernieres bases MaxMind GeoLite2 (City + ASN) utilisees par
 * GeoIpLookupService. Necessite un compte MaxMind gratuit (app.geoip.account-id/license-key,
 * cf. le prerequis du module historique de connexion) - en leur absence, ce job se contente de
 * logger un avertissement et reste inactif, il ne fait jamais echouer le demarrage de l'application.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpDatabaseUpdateScheduler {

    private static final String DOWNLOAD_URL_TEMPLATE = "https://download.maxmind.com/geoip/databases/%s/download?suffix=tar.gz";

    private final GeoIpLookupService geoIpLookupService;

    @Value("${app.geoip.account-id:}")
    private String accountId;

    @Value("${app.geoip.license-key:}")
    private String licenseKey;

    @Value("${app.geoip.city-db-path:}")
    private String cityDbPath;

    @Value("${app.geoip.asn-db-path:}")
    private String asnDbPath;

    private final OkHttpClient httpClient = new OkHttpClient();

    @Scheduled(cron = "0 0 3 1 * *", zone = "Africa/Abidjan")
    public void updateDatabases() {
        if (!StringUtils.hasText(accountId) || !StringUtils.hasText(licenseKey)) {
            log.warn("Mise a jour GeoLite2 ignoree : app.geoip.account-id/license-key non configures");
            return;
        }
        boolean cityOk = updateOne("GeoLite2-City", cityDbPath);
        boolean asnOk = updateOne("GeoLite2-ASN", asnDbPath);
        if (cityOk || asnOk) {
            geoIpLookupService.reload();
        }
    }

    private boolean updateOne(String editionId, String targetPath) {
        if (!StringUtils.hasText(targetPath)) {
            log.info("Mise a jour {} ignoree : chemin cible non configure (app.geoip.*-db-path)", editionId);
            return false;
        }
        try {
            Path target = Path.of(targetPath).toAbsolutePath();
            Files.createDirectories(target.getParent());
            byte[] archive = download(editionId);
            // Fichier temporaire cree dans le MEME repertoire que la cible : necessaire pour que
            // Files.move(..., ATOMIC_MOVE) fonctionne (un move atomique echoue entre deux disques
            // differents, ex. C:\...\Temp vers D:\..., frequent sur ce poste Windows).
            Path mmdb = extractMmdb(archive, editionId, target.getParent());
            Files.move(mmdb, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Base {} mise a jour avec succes -> {}", editionId, target);
            return true;
        } catch (IOException e) {
            log.warn("Echec de la mise a jour de la base {} : {}", editionId, e.getMessage());
            return false;
        }
    }

    private byte[] download(String editionId) throws IOException {
        Request request = new Request.Builder()
                .url(String.format(DOWNLOAD_URL_TEMPLATE, editionId))
                .header("Authorization", Credentials.basic(accountId, licenseKey))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Reponse HTTP " + response.code() + " lors du telechargement de " + editionId);
            }
            return response.body().bytes();
        }
    }

    /** L'archive MaxMind contient un dossier horodate avec le .mmdb dedans, plus des fichiers annexes (COPYRIGHT.txt...). */
    private Path extractMmdb(byte[] archiveBytes, String editionId, Path targetDirectory) throws IOException {
        Path tempFile = Files.createTempFile(targetDirectory, "geoip-" + editionId, ".mmdb.tmp");
        try (InputStream bytesIn = new java.io.ByteArrayInputStream(archiveBytes);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(new BufferedInputStream(bytesIn));
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".mmdb")) {
                    Files.copy(tarIn, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    return tempFile;
                }
            }
        }
        Files.deleteIfExists(tempFile);
        throw new IOException("Aucun fichier .mmdb trouve dans l'archive " + editionId);
    }
}
