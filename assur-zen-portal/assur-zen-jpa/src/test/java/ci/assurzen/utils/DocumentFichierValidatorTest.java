package ci.assurzen.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Controle serveur des fichiers joints aux partenaires (2026-10-05). */
class DocumentFichierValidatorTest {

    private static final byte[] PDF = "%PDF-1.4\n".getBytes();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void formatsAdmis_typeControleRenvoye() {
        assertEquals("application/pdf", DocumentFichierValidator.verifier("cni.PDF", "application/pdf", PDF));
        assertEquals("image/jpeg", DocumentFichierValidator.verifier("photo.jpeg", "image/jpg", JPEG));
        assertEquals("image/png", DocumentFichierValidator.verifier("rib.png", null, PNG));
        assertEquals("application/pdf", DocumentFichierValidator.verifier("cni.pdf", "application/octet-stream", PDF));
    }

    @Test
    void executable_extensionIncoherente_typeDeclareIncoherent_refuses() {
        assertRefus("script.exe", "application/x-msdownload", "MZ".getBytes());
        assertRefus("script.pdf", "application/pdf", "MZ\u0090\u0000\u0003\u0000\u0000\u0000".getBytes());
        assertRefus("image.png", "image/png", PDF);
        assertRefus("cni.pdf", "text/html", PDF);
        assertRefus("sans-extension", "application/pdf", PDF);
        assertRefus("vide.pdf", "application/pdf", new byte[0]);
    }

    @Test
    void estFormatAdmis() {
        assertTrue(DocumentFichierValidator.estFormatAdmis("image/png"));
        assertFalse(DocumentFichierValidator.estFormatAdmis("application/x-msdownload"));
        assertFalse(DocumentFichierValidator.estFormatAdmis(null));
    }

    private static void assertRefus(String nom, String type, byte[] contenu) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> DocumentFichierValidator.verifier(nom, type, contenu));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(DocumentFichierValidator.MESSAGE_TYPE, ex.getReason());
    }

    @Test
    void nettoyerNom_sansCheminNiCaracteresDangereux() {
        assertEquals("cni.pdf", DocumentFichierValidator.nettoyerNom("C:\\Users\\moi\\cni.pdf"));
        assertEquals("passwd.pdf", DocumentFichierValidator.nettoyerNom("../../etc/passwd.pdf"));
        assertEquals("a_b_.pdf", DocumentFichierValidator.nettoyerNom("a\"b\n.pdf"));
        assertEquals("document", DocumentFichierValidator.nettoyerNom(".."));
        assertEquals("document", DocumentFichierValidator.nettoyerNom(null));
    }

    @Test
    void refus_exceptionAvecCodeStable() {
        assertThrows(ci.assurzen.exception.DocumentTypeNonAutoriseException.class,
                () -> DocumentFichierValidator.verifier("script.exe", "application/x-msdownload", "MZ".getBytes()));
    }
}
