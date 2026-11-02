package ci.assurzen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDocumentStorageServiceTest {

    private LocalDocumentStorageService newService(Path tempDir) {
        LocalDocumentStorageService service = new LocalDocumentStorageService();
        ReflectionTestUtils.setField(service, "gedDir", tempDir.toString());
        return service;
    }

    @Test
    void store_calculeLeChecksumSha256Correctement(@TempDir Path tempDir) throws Exception {
        LocalDocumentStorageService service = newService(tempDir);
        byte[] content = "contenu-du-document".getBytes();

        StorageResult result = service.store("CONTRAT/2026/09/uuid-1/v1", "contrat.pdf", new ByteArrayInputStream(content));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedChecksum = HexFormat.of().formatHex(digest.digest(content));
        assertEquals(expectedChecksum, result.checksumSha256());
        assertEquals(content.length, result.sizeBytes());
        assertTrue(Path.of(result.storedPath()).toFile().exists());
    }

    @Test
    void storeThenRead_renvoieLeMemeContenu(@TempDir Path tempDir) {
        LocalDocumentStorageService service = newService(tempDir);
        byte[] content = "un autre contenu".getBytes();

        StorageResult result = service.store("AUTRE/2026/09/uuid-2/v1", "fichier.pdf", new ByteArrayInputStream(content));
        byte[] readBack = service.read(result.storedPath());

        assertArrayEquals(content, readBack);
    }

    @Test
    void delete_supprimeLeFichierDuDisque(@TempDir Path tempDir) {
        LocalDocumentStorageService service = newService(tempDir);
        StorageResult result = service.store("AUTRE/2026/09/uuid-3/v1", "a-supprimer.pdf",
                new ByteArrayInputStream("x".getBytes()));

        service.delete(result.storedPath());

        assertFalse(Path.of(result.storedPath()).toFile().exists());
    }

    @Test
    void delete_fichierDejaAbsent_neLeveAucuneException(@TempDir Path tempDir) {
        LocalDocumentStorageService service = newService(tempDir);
        service.delete(tempDir.resolve("inexistant.pdf").toString());
    }
}
