package ci.assurzen.utils;

import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Controle serveur des fichiers joints aux partenaires (2026-10-05, audit de l'Espace Partenaire : un
 * "script.exe" declare application/x-msdownload etait accepte). Seuls PDF, JPEG et PNG sont admis, et le
 * fichier doit l'etre REELLEMENT : extension, type declare ET premiers octets (signature) doivent concorder.
 * Le type enregistre puis renvoye au telechargement est celui deduit de la signature, jamais celui du client.
 */
public final class DocumentFichierValidator {

    public static final String MESSAGE_TYPE = "Format de fichier non autorise : seuls les fichiers PDF, JPG et PNG sont acceptes";

    private enum Format {
        PDF("application/pdf", Set.of("pdf"), new byte[]{0x25, 0x50, 0x44, 0x46}),                 // %PDF
        JPEG("image/jpeg", Set.of("jpg", "jpeg"), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("image/png", Set.of("png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        final String contentType;
        final Set<String> extensions;
        final byte[] signature;

        Format(String contentType, Set<String> extensions, byte[] signature) {
            this.contentType = contentType;
            this.extensions = extensions;
            this.signature = signature;
        }
    }

    /** Types declares tolerees par format (certains navigateurs envoient image/jpg ou image/pjpeg). */
    private static final Map<Format, Set<String>> TYPES_DECLARES = Map.of(
            Format.PDF, Set.of("application/pdf", "application/x-pdf"),
            Format.JPEG, Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
            Format.PNG, Set.of("image/png", "image/x-png"));

    private DocumentFichierValidator() {
    }

    /**
     * @param nomFichier  nom d'origine (extension controlee)
     * @param typeDeclare Content-Type de la partie multipart (peut etre null : seule la signature fait foi)
     * @param debut       premiers octets du fichier (au moins 8)
     * @return le type de contenu controle a enregistrer
     * @throws ResponseStatusException 400 si le fichier n'est pas un PDF, JPEG ou PNG authentique
     */
    public static String verifier(String nomFichier, String typeDeclare, byte[] debut) {
        Format format = Arrays.stream(Format.values())
                .filter(f -> commencePar(debut, f.signature))
                .findFirst()
                .orElseThrow(DocumentFichierValidator::refus);
        String extension = extension(nomFichier);
        if (extension == null || !format.extensions.contains(extension)) {
            throw refus();
        }
        if (typeDeclare != null && !typeDeclare.isBlank()) {
            String type = typeDeclare.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!"application/octet-stream".equals(type) && !TYPES_DECLARES.get(format).contains(type)) {
                throw refus();
            }
        }
        return format.contentType;
    }

    /** Vrai si ce type de contenu (deja enregistre) est un des formats admis, servis sans risque en ligne. */
    public static boolean estFormatAdmis(String contentType) {
        return contentType != null && Arrays.stream(Format.values()).anyMatch(f -> f.contentType.equalsIgnoreCase(contentType));
    }

    private static boolean commencePar(byte[] contenu, byte[] signature) {
        if (contenu == null || contenu.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (contenu[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String nomFichier) {
        if (nomFichier == null) {
            return null;
        }
        int point = nomFichier.lastIndexOf('.');
        return point < 0 || point == nomFichier.length() - 1 ? null : nomFichier.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException refus() {
        return new ci.assurzen.exception.DocumentTypeNonAutoriseException(MESSAGE_TYPE);
    }

    /**
     * Nom de fichier sur : sans chemin (ni / ni \), sans caracteres de controle, 255 caracteres au plus.
     * Utilise pour le nom enregistre et donc pour le Content-Disposition du telechargement.
     */
    public static String nettoyerNom(String nomFichier) {
        if (nomFichier == null) {
            return "document";
        }
        String nom = nomFichier.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (nom.isEmpty() || nom.equals(".") || nom.equals("..")) {
            return "document";
        }
        return nom.length() > 255 ? nom.substring(nom.length() - 255) : nom;
    }
}
