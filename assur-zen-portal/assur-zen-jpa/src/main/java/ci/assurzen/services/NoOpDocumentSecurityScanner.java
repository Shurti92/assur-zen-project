package ci.assurzen.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/** Aucun antivirus n'est configure dans ce projet aujourd'hui - renvoie toujours CLEAN sans lire le
 * flux (pour ne jamais consommer l'InputStream que l'appelant doit encore utiliser pour le stockage). */
@Service
@Slf4j
public class NoOpDocumentSecurityScanner implements DocumentSecurityScanner {

    @Override
    public ScanResult scan(InputStream file) {
        log.debug("Antivirus non configure - fichier accepte sans analyse de contenu reelle");
        return new ScanResult(ScanStatus.CLEAN, "Aucun antivirus configure (NoOpDocumentSecurityScanner)");
    }
}
