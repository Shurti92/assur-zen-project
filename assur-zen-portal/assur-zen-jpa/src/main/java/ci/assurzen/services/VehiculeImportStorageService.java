package ci.assurzen.services;

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
public class VehiculeImportStorageService {

    @Value("${app.uploads.fleet-imports-dir}")
    private String fleetImportsDir;

    public String store(Long fleetId, String originalFileName, InputStream content) {
        try {
            Path dir = Paths.get(fleetImportsDir, String.valueOf(fleetId));
            Files.createDirectories(dir);

            String safeName = StringUtils.hasText(originalFileName)
                    ? Paths.get(originalFileName).getFileName().toString()
                    : "import";
            String storedFileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(storedFileName);

            Files.copy(content, target);
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du fichier d'import", e);
        }
    }

    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier d'import introuvable", e);
        }
    }
}
