package ci.assurzen.services;

import java.io.InputStream;

/**
 * Abstraction de stockage des fichiers GED (§7-8 du cahier des charges) : la logique metier ne
 * doit jamais coupler directement a un provider (disque local, S3, Azure Blob, MinIO...). Seule
 * LocalDocumentStorageService implemente cette interface dans ce projet aujourd'hui - brancher un
 * autre provider plus tard n'exige pas de modifier GedDocumentService.
 */
public interface DocumentStorageService {

    StorageResult store(String relativeDir, String originalFileName, InputStream content);

    byte[] read(String storedPath);

    void delete(String storedPath);
}
