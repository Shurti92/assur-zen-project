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
public class PaiementStorageService {

    @Value("${app.uploads.paiements-dir}")
    private String paiementsDir;

    public String store(Long paiementId, String originalFileName, InputStream content) {
        try {
            Path dir = Paths.get(paiementsDir, String.valueOf(paiementId));
            Files.createDirectories(dir);

            String safeName = StringUtils.hasText(originalFileName)
                    ? Paths.get(originalFileName).getFileName().toString()
                    : "piece-jointe";
            String storedFileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(storedFileName);

            Files.copy(content, target);
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement de la piece jointe", e);
        }
    }

    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable", e);
        }
    }
}
