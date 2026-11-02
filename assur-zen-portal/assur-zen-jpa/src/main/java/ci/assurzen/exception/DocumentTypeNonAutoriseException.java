package ci.assurzen.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Fichier joint qui n'est pas un PDF, JPEG ou PNG authentique (2026-10-05) : 400, code stable
 * DOCUMENT_TYPE_NON_AUTORISE (GlobalExceptionHandler) pour que les applications affichent un message adapte. */
public class DocumentTypeNonAutoriseException extends ResponseStatusException {

    public static final String CODE = "DOCUMENT_TYPE_NON_AUTORISE";

    public DocumentTypeNonAutoriseException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
