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
import java.util.UUID;

@Service
@Slf4j
public class PartenaireDocumentStorageService {

    @Value("${app.uploads.partenaires-dir}")
    private String partenairesDir;

    public String store(Long partenaireId, String originalFileName, InputStream content) {
        try {
            Path dir = Paths.get(partenairesDir, String.valueOf(partenaireId));
            Files.createDirectories(dir);

            String safeName = StringUtils.hasText(originalFileName)
                    ? Paths.get(originalFileName).getFileName().toString()
                    : "document";
            String storedFileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(storedFileName);

            Files.copy(content, target);
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du document", e);
        }
    }

    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable", e);
        }
    }

    /** Best-effort : la suppression en base (l'operation metier) ne doit jamais echouer a cause d'un
     * probleme disque (fichier deja absent, permissions...) - meme philosophie de tolerance que le
     * reste des services de stockage de ce projet (ex. GeoIpLookupService). */
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (IOException e) {
            log.warn("Echec non bloquant de la suppression du fichier {} : {}", storedPath, e.getMessage());
        }
    }
}
