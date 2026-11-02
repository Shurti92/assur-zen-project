package ci.assurzen.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Conserve le fichier CSV uploade (necessaire pour /retry, qui rejoue le meme fichier). Meme pattern que SinistrePhotoStorageService. */
@Service
public class SatisfactionImportStorageService {

    @Value("${app.uploads.satisfaction-imports-dir}")
    private String importsDir;

    public String store(String code, String originalFileName, InputStream content) {
        try {
            Path dir = Paths.get(importsDir, code);
            Files.createDirectories(dir);

            String safeName = StringUtils.hasText(originalFileName)
                    ? Paths.get(originalFileName).getFileName().toString()
                    : "import.csv";
            String storedFileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(storedFileName);

            Files.copy(content, target);
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du fichier CSV", e);
        }
    }

    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier CSV introuvable: " + storedPath, e);
        }
    }
}
