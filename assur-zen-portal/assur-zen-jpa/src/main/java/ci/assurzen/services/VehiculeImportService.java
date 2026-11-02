package ci.assurzen.services;

import ci.assurzen.dto.FleetImportErrorResponse;
import ci.assurzen.dto.FleetImportResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.FleetImport;
import ci.assurzen.entities.FleetImportError;
import ci.assurzen.entities.FleetImportFormat;
import ci.assurzen.entities.FleetImportStatus;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.FleetImportErrorRepository;
import ci.assurzen.repositories.FleetImportRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VehiculeImportService {

    private final FleetImportRepository fleetImportRepository;
    private final FleetImportErrorRepository fleetImportErrorRepository;
    private final VehiculeImportStorageService vehiculeImportStorageService;
    private final VehiculeImportRowProcessor vehiculeImportRowProcessor;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FleetService fleetService;

    private static final List<FleetImportStatus> STATUTS_BLOQUANT_REIMPORT = List.of(
            FleetImportStatus.READY, FleetImportStatus.PROCESSING,
            FleetImportStatus.COMPLETED, FleetImportStatus.PARTIALLY_COMPLETED
    );

    public byte[] template(FleetImportFormat format) {
        if (format == FleetImportFormat.CSV) {
            return String.join(",", VehiculeImportParser.COLONNES_ATTENDUES).getBytes(StandardCharsets.UTF_8);
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vehicules");
            Row header = sheet.createRow(0);
            List<String> colonnes = VehiculeImportParser.COLONNES_ATTENDUES;
            for (int i = 0; i < colonnes.size(); i++) {
                header.createCell(i).setCellValue(colonnes.get(i));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public FleetImportResponse preview(Long fleetId, MultipartFile file) {
        Fleet fleet = fleetService.resolveFleet(fleetId);
        fleetService.requireFleetActive(fleet);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        long maxFileSize = dynamicConfigurationService.getLong("MAX_FILE_SIZE", 10L * 1024 * 1024);
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier trop volumineux (" + file.getSize() + " octets, maximum autorise: " + maxFileSize + " octets, configurable via MAX_FILE_SIZE)");
        }

        FleetImportFormat format = detecterFormat(file.getOriginalFilename());
        byte[] contenu;
        try {
            contenu = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String checksum = sha256(contenu);

        fleetImportRepository.findByFleetIdAndFileChecksumAndStatusIn(fleetId, checksum, STATUTS_BLOQUANT_REIMPORT)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ce fichier a deja ete importe ou est en attente de confirmation (import #" + existing.getId() + ")");
                });

        List<LigneVehiculeBrute> lignes;
        try {
            lignes = format == FleetImportFormat.CSV
                    ? VehiculeImportParser.parseCsv(new ByteArrayInputStream(contenu))
                    : VehiculeImportParser.parseXlsx(new ByteArrayInputStream(contenu));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier illisible ou format invalide: " + e.getMessage());
        }
        ResultatAnalyseImportVehicules analyse = VehiculeImportParser.analyser(lignes);

        String storedPath = vehiculeImportStorageService.store(fleetId, file.getOriginalFilename(), new ByteArrayInputStream(contenu));

        FleetImport fleetImport = FleetImport.builder()
                .fleet(fleet)
                .fileName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "import")
                .storedPath(storedPath)
                .fileChecksum(checksum)
                .format(format)
                .status(analyse.lignesValides().isEmpty() ? FleetImportStatus.FAILED : FleetImportStatus.READY)
                .totalRows(analyse.totalRows())
                .validRows(analyse.lignesValides().size())
                .invalidRows(analyse.erreurs().size())
                .duplicateRows(analyse.duplicateRows())
                .uploadedBy(resolveUser())
                .build();
        fleetImport = fleetImportRepository.save(fleetImport);

        if (!analyse.erreurs().isEmpty()) {
            FleetImport finalImport = fleetImport;
            List<FleetImportError> erreurs = analyse.erreurs().stream()
                    .map(e -> FleetImportError.builder()
                            .fleetImport(finalImport)
                            .ligneNumero(e.ligneNumero())
                            .colonneEnErreur(e.colonne())
                            .valeurBrute(tronquer(e.valeurBrute(), 500))
                            .message(e.message())
                            .build())
                    .toList();
            fleetImportErrorRepository.saveAll(erreurs);
        }

        return toResponse(fleetImport);
    }

    /** Le traitement ligne-a-ligne (VehiculeImportRowProcessor) tourne en dehors de la transaction de
     * cette methode (chaque ligne a sa propre transaction REQUIRES_NEW) - cette methode elle-meme
     * n'est donc PAS @Transactional : elle orchestre, elle n'ecrit rien directement sauf au debut
     * (passage a PROCESSING) et a la fin (rapport final), chacun dans sa propre petite transaction
     * implicite via les repositories (save() seul, hors @Transactional explicite, s'auto-commit). */
    public FleetImportResponse confirm(Long fleetId, Long importId) {
        FleetImport fleetImport = fleetImportRepository.findById(importId)
                .filter(imp -> imp.getFleet().getId().equals(fleetId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable: " + importId));
        if (fleetImport.getStatus() != FleetImportStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet import n'est pas au statut READY (statut actuel: " + fleetImport.getStatus() + ")");
        }

        Fleet fleet = fleetService.resolveFleet(fleetId);
        fleetService.requireFleetActive(fleet);

        fleetImport.setStatus(FleetImportStatus.PROCESSING);
        fleetImport.setStartedAt(Instant.now());
        fleetImport = fleetImportRepository.save(fleetImport);

        // Filet de securite : sans ce try/catch, toute erreur imprevue (relecture du fichier,
        // recalcul des compteurs...) laisserait l'import bloque a PROCESSING pour toujours, sans
        // aucun moyen de le relancer (bug reel constate en test E2E - une DataIntegrityViolationException
        // levee pendant la creation d'un Vehicule, non rattrapee par la boucle ligne par ligne a
        // l'epoque, a fait planter confirm() en plein milieu). Le rapport passe alors a FAILED plutot
        // que de rester bloque, et l'exception d'origine est quand meme renvoyee au client (500).
        try {
            byte[] contenu = vehiculeImportStorageService.read(fleetImport.getStoredPath());
            List<LigneVehiculeBrute> lignes;
            try {
                lignes = fleetImport.getFormat() == FleetImportFormat.CSV
                        ? VehiculeImportParser.parseCsv(new ByteArrayInputStream(contenu))
                        : VehiculeImportParser.parseXlsx(new ByteArrayInputStream(contenu));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de relecture du fichier d'import", e);
            }
            ResultatAnalyseImportVehicules analyse = VehiculeImportParser.analyser(lignes);

            int succes = 0;
            List<FleetImportError> nouvellesErreurs = new ArrayList<>();
            for (LigneVehiculeValidee ligne : analyse.lignesValides()) {
                try {
                    vehiculeImportRowProcessor.importerLigne(fleetId, ligne);
                    succes++;
                } catch (RuntimeException e) {
                    // RuntimeException au sens large (pas seulement ResponseStatusException) : une
                    // ligne peut aussi echouer sur une contrainte DB imprevue (DataIntegrityViolationException,
                    // etc.) - une seule ligne fautive ne doit jamais faire echouer tout le reste de
                    // l'import (leçon deja payee sur l'import Satisfaction).
                    String message = e instanceof ResponseStatusException rse && rse.getReason() != null
                            ? rse.getReason()
                            : "Erreur technique lors de l'import de cette ligne, veuillez reessayer";
                    nouvellesErreurs.add(FleetImportError.builder()
                            .fleetImport(fleetImport)
                            .ligneNumero(ligne.ligneNumero())
                            .colonneEnErreur("immatriculation")
                            .valeurBrute(ligne.immatriculation())
                            .message(message)
                            .build());
                }
            }
            int echecs = nouvellesErreurs.size();
            if (!nouvellesErreurs.isEmpty()) {
                fleetImportErrorRepository.saveAll(nouvellesErreurs);
            }

            fleetService.recomputeCounters(fleet);
            // notifierIncorporationApresImport(), pas notifierIncorporation(fleet, ...) directement :
            // confirm() n'est pas transactionnel, "fleet" (charge en debut de methode) est detache a ce
            // stade - un appel direct levait LazyInitializationException des que la flotte n'avait pas
            // de managerPhone (repli sur fleet.getClient().getClientPhone(), bug reel du 2026-09-13).
            Boolean notificationEnvoyee = fleetService.notifierIncorporationApresImport(fleetId, succes);

            fleetImport.setSuccessCount(succes);
            fleetImport.setErrorCount(echecs);
            fleetImport.setFinishedAt(Instant.now());
            fleetImport.setStatus(succes == 0 ? FleetImportStatus.FAILED
                    : (echecs == 0 ? FleetImportStatus.COMPLETED : FleetImportStatus.PARTIALLY_COMPLETED));
            fleetImport = fleetImportRepository.save(fleetImport);

            return toResponse(fleetImport, notificationEnvoyee);
        } catch (RuntimeException e) {
            fleetImport.setStatus(FleetImportStatus.FAILED);
            fleetImport.setFinishedAt(Instant.now());
            fleetImportRepository.save(fleetImport);
            throw e;
        }
    }

    public PageResponse<FleetImportResponse> list(Long fleetId, FleetImportStatus status, Integer page, Integer size) {
        fleetService.resolveFleet(fleetId);
        List<FleetImportResponse> items = fleetImportRepository.findByFleetIdOrderByCreatedAtDesc(fleetId).stream()
                .filter(imp -> status == null || imp.getStatus() == status)
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public FleetImportResponse get(Long fleetId, Long importId) {
        return toResponse(resolveImport(fleetId, importId));
    }

    public PageResponse<FleetImportErrorResponse> listErrors(Long fleetId, Long importId, Integer page, Integer size) {
        FleetImport fleetImport = resolveImport(fleetId, importId);
        List<FleetImportErrorResponse> items = fleetImportErrorRepository.findByFleetImportIdOrderByLigneNumeroAsc(fleetImport.getId()).stream()
                .map(e -> new FleetImportErrorResponse(e.getId(), e.getLigneNumero(), e.getColonneEnErreur(), e.getValeurBrute(), e.getMessage()))
                .toList();
        return paginate(items, page, size);
    }

    private FleetImport resolveImport(Long fleetId, Long importId) {
        return fleetImportRepository.findById(importId)
                .filter(imp -> imp.getFleet().getId().equals(fleetId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable: " + importId));
    }

    private User resolveUser() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));
    }

    private FleetImportFormat detecterFormat(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de fichier manquant");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return FleetImportFormat.CSV;
        }
        if (lower.endsWith(".xlsx")) {
            return FleetImportFormat.XLSX;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format de fichier non supporte (attendu: .csv ou .xlsx)");
    }

    private String sha256(byte[] contenu) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contenu);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String tronquer(String valeur, int max) {
        return valeur != null && valeur.length() > max ? valeur.substring(0, max) : valeur;
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private FleetImportResponse toResponse(FleetImport fleetImport) {
        return toResponse(fleetImport, null);
    }

    private FleetImportResponse toResponse(FleetImport fleetImport, Boolean notificationEnvoyee) {
        return new FleetImportResponse(
                fleetImport.getId(),
                fleetImport.getFleet() == null ? null : fleetImport.getFleet().getId(),
                fleetImport.getFileName(),
                fleetImport.getFormat(),
                fleetImport.getStatus(),
                fleetImport.getTotalRows(),
                fleetImport.getValidRows(),
                fleetImport.getInvalidRows(),
                fleetImport.getDuplicateRows(),
                fleetImport.getSuccessCount(),
                fleetImport.getErrorCount(),
                fleetService.displayName(fleetImport.getUploadedBy()),
                fleetImport.getCreatedAt(),
                fleetImport.getStartedAt(),
                fleetImport.getFinishedAt(),
                notificationEnvoyee
        );
    }
}
