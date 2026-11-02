package ci.assurzen.services;

import java.io.InputStream;
import java.util.Optional;

/**
 * Preparation future (§57) : extraction de texte/numero de contrat/immatriculation/client par OCR.
 * Aucune implementation dans cette phase - pas de bean Spring, pas d'appel depuis
 * GedDocumentService. A cabler quand un besoin d'OCR reel se presente.
 */
public interface DocumentOcrService {

    Optional<String> extractText(InputStream file, String mimeType);
}
