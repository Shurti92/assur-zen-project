package ci.assurzen.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Seule implementation de DocumentStorageService dans ce projet aujourd'hui (§8 : Azure/S3/MinIO
 * restent branchables plus tard sans toucher au metier). Meme patron que les 5 autres storage
 * services deja presents (ex. PartenaireDocumentStorageService), plus le calcul SHA-256 en un seul
 * passage disque (DigestInputStream) pour eviter de relire le fichier une seconde fois. */
@Service
@Slf4j
public class LocalDocumentStorageService implements DocumentStorageService {

    @Value("${app.uploads.ged-dir}")
    private String gedDir;

    @Override
    public StorageResult store(String relativeDir, String originalFileName, InputStream content) {
        try {
            Path dir = Paths.get(gedDir, relativeDir);
            Files.createDirectories(dir);

            String safeName = StringUtils.hasText(originalFileName)
                    ? Paths.get(originalFileName).getFileName().toString()
                    : "document";
            String storedFileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(storedFileName);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestStream = new DigestInputStream(content, digest)) {
                Files.copy(digestStream, target);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());

            return new StorageResult(target.toString(), storedFileName, Files.size(target), checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du document", e);
        }
    }

    @Override
    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable", e);
        }
    }

    /** Best-effort : meme philosophie de tolerance que les autres storage services de ce projet
     * (ex. PartenaireDocumentStorageService.delete). */
    @Override
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (IOException e) {
            log.warn("Echec non bloquant de la suppression du fichier {} : {}", storedPath, e.getMessage());
        }
    }
}
