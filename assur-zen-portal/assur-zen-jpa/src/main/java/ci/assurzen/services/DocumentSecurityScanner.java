package ci.assurzen.services;

import java.io.InputStream;

/**
 * Point d'integration futur pour un antivirus (ClamAV, service cloud...) - §27 du cahier des
 * charges. NoOpDocumentSecurityScanner est la seule implementation dans ce projet aujourd'hui : le
 * flux d'upload appelle deja cette interface, donc brancher un vrai scanner plus tard ne change pas
 * GedDocumentService.
 */
public interface DocumentSecurityScanner {

    ScanResult scan(InputStream file);

    enum ScanStatus { CLEAN, INFECTED, SCAN_UNAVAILABLE }

    record ScanResult(ScanStatus status, String details) {
    }
}
