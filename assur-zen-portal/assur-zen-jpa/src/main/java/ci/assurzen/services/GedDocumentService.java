package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentLinkRequest;
import ci.assurzen.dto.GedDocumentLinkResponse;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.dto.GedDocumentVersionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.UpdateGedDocumentRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.GedConfidentialityLevel;
import ci.assurzen.entities.GedDocument;
import ci.assurzen.entities.GedDocumentCategory;
import ci.assurzen.entities.GedDocumentLink;
import ci.assurzen.entities.GedDocumentStatus;
import ci.assurzen.entities.GedDocumentType;
import ci.assurzen.entities.GedDocumentVersion;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.GedDocumentLinkRepository;
import ci.assurzen.repositories.GedDocumentRepository;
import ci.assurzen.repositories.GedDocumentVersionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service principal du module GED (noyau transversal, Phase 1). Un document logique (GedDocument)
 * ne contient jamais le fichier lui-meme : le contenu vit dans GedDocumentVersion (jamais ecrasee,
 * §6), le rattachement a un objet metier passe par GedDocumentLink (entity_type/entity_id
 * generique, §20).
 */
@Service
@RequiredArgsConstructor
public class GedDocumentService {

    private static final List<String> DEFAULT_ALLOWED_EXTENSIONS = List.of("pdf", "png", "jpg", "jpeg", "docx", "xlsx", "csv");

    private final GedDocumentRepository documentRepository;
    private final GedDocumentVersionRepository versionRepository;
    private final GedDocumentLinkRepository linkRepository;
    private final GedDocumentTypeService documentTypeService;
    private final GedDocumentCategoryService documentCategoryService;
    private final DocumentStorageService documentStorageService;
    private final DocumentSecurityScanner documentSecurityScanner;
    private final CodeGeneratorService codeGeneratorService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final AuthService authService;

    // ---------------------------------------------------------------- Creation / versions

    @Transactional
    public GedDocumentResponse create(Long documentTypeId, Long categoryId, String title, String description,
                                       String entityType, Long entityId, GedConfidentialityLevel confidentialityLevel,
                                       String source, MultipartFile file) {
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre du document est obligatoire");
        }
        GedDocumentType documentType = documentTypeService.getEntity(documentTypeId);
        GedDocumentCategory category = categoryId != null ? documentCategoryService.getEntity(categoryId) : null;
        validateFile(file);

        String documentNumber = codeGeneratorService.generate(CodePrefix.GED);
        String uuid = UUID.randomUUID().toString();
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();

        StorageResult storageResult = storeFile(documentType.getCode(), uuid, 1, file);

        GedDocument document = GedDocument.builder()
                .uuid(uuid)
                .documentNumber(documentNumber)
                .documentType(documentType)
                .category(category)
                .title(title)
                .description(description)
                .status(GedDocumentStatus.UPLOADED)
                .confidentialityLevel(confidentialityLevel != null ? confidentialityLevel : GedConfidentialityLevel.INTERNAL)
                .source(StringUtils.hasText(source) ? source : "UPLOAD")
                .checksumSha256(storageResult.checksumSha256())
                .build();
        document.setCreatedBy(principal.username());
        document.setUpdatedBy(principal.username());
        document = documentRepository.save(document);

        saveVersion(document, 1, file, storageResult, principal.username());

        if (StringUtils.hasText(entityType) && entityId != null) {
            createLink(document, entityType, entityId, "ATTACHMENT", principal.username());
        }

        functionalAuditRecorder.recordAction("GED", "GedDocument", documentNumber, ActionAudit.CREATE,
                "Document cree (" + documentType.getCode() + ")");

        return toResponse(document);
    }

    @Transactional
    public GedDocumentVersionResponse addVersion(Long documentId, MultipartFile file) {
        GedDocument document = getEntity(documentId);
        if (document.getStatus() == GedDocumentStatus.ARCHIVED || document.getStatus() == GedDocumentStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible d'ajouter une version : document " + document.getStatus());
        }
        validateFile(file);

        int nextVersionNumber = versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        StorageResult storageResult = storeFile(document.getDocumentType().getCode(), document.getUuid(), nextVersionNumber, file);
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();

        GedDocumentVersion version = saveVersion(document, nextVersionNumber, file, storageResult, principal.username());

        document.setChecksumSha256(storageResult.checksumSha256());
        document.setUpdatedBy(principal.username());
        documentRepository.save(document);

        functionalAuditRecorder.recordAction("GED", "GedDocumentVersion", String.valueOf(version.getId()), ActionAudit.CREATE,
                "Nouvelle version " + nextVersionNumber + " du document " + document.getDocumentNumber());

        return toVersionResponse(version);
    }

    public List<GedDocumentVersionResponse> listVersions(Long documentId) {
        getEntity(documentId);
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    public GedDocumentVersionContent download(Long documentId) {
        GedDocument document = getEntity(documentId);
        GedDocumentVersion version = versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune version disponible pour ce document"));
        return downloadVersion(document, version);
    }

    public GedDocumentVersionContent downloadVersion(Long documentId, Long versionId) {
        GedDocument document = getEntity(documentId);
        GedDocumentVersion version = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version introuvable: " + versionId));
        return downloadVersion(document, version);
    }

    private GedDocumentVersionContent downloadVersion(GedDocument document, GedDocumentVersion version) {
        byte[] content = documentStorageService.read(version.getStoredPath());
        functionalAuditRecorder.recordAction("GED", "GedDocument", document.getDocumentNumber(), ActionAudit.DOWNLOAD,
                "Version " + version.getVersionNumber() + " telechargee");
        return new GedDocumentVersionContent(content, version.getMimeType(), version.getOriginalFileName());
    }

    // ---------------------------------------------------------------- Consultation / recherche

    public GedDocumentResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public PageResponse<GedDocumentResponse> search(String documentNumber, String documentType, String category,
                                                      GedDocumentStatus status, String entityType, Long entityId,
                                                      String createdBy, LocalDate dateFrom, LocalDate dateTo,
                                                      GedConfidentialityLevel confidentialityLevel,
                                                      Integer page, Integer size) {
        return paginate(filter(documentNumber, documentType, category, status, entityType, entityId, createdBy, dateFrom, dateTo, confidentialityLevel), page, size);
    }

    public PageResponse<GedDocumentResponse> listByEntity(String entityType, Long entityId, Integer page, Integer size) {
        Set<Long> documentIds = linkRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream()
                .map(link -> link.getDocument().getId())
                .collect(Collectors.toCollection(HashSet::new));

        List<GedDocumentResponse> items = documentRepository.findAllById(documentIds).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    // ---------------------------------------------------------------- Metadonnees / transitions

    @Transactional
    public GedDocumentResponse update(Long id, UpdateGedDocumentRequest request) {
        GedDocument document = getEntity(id);
        GedDocumentCategory category = request.categoryId() != null ? documentCategoryService.getEntity(request.categoryId()) : null;

        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setCategory(category);
        if (request.confidentialityLevel() != null) {
            document.setConfidentialityLevel(request.confidentialityLevel());
        }
        document.setUpdatedBy(authService.currentPrincipal().username());

        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public GedDocumentResponse validate(Long id) {
        GedDocument document = getEntity(id);
        requireUploaded(document);
        document.setStatus(GedDocumentStatus.VALIDATED);
        document.setUpdatedBy(authService.currentPrincipal().username());
        documentRepository.save(document);

        functionalAuditRecorder.recordAction("GED", "GedDocument", document.getDocumentNumber(), ActionAudit.VALIDATE, null);
        return toResponse(document);
    }

    @Transactional
    public GedDocumentResponse reject(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de rejet est obligatoire");
        }
        GedDocument document = getEntity(id);
        requireUploaded(document);
        document.setStatus(GedDocumentStatus.REJECTED);
        document.setRejectionReason(reason);
        document.setUpdatedBy(authService.currentPrincipal().username());
        documentRepository.save(document);

        functionalAuditRecorder.recordAction("GED", "GedDocument", document.getDocumentNumber(), ActionAudit.REJECT, reason);
        return toResponse(document);
    }

    @Transactional
    public GedDocumentResponse archive(Long id) {
        GedDocument document = getEntity(id);
        if (document.getStatus() == GedDocumentStatus.ARCHIVED || document.getStatus() == GedDocumentStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document deja " + document.getStatus());
        }
        String username = authService.currentPrincipal().username();
        document.setStatus(GedDocumentStatus.ARCHIVED);
        document.setArchivedAt(Instant.now());
        document.setArchivedBy(username);
        document.setUpdatedBy(username);
        documentRepository.save(document);

        functionalAuditRecorder.recordAction("GED", "GedDocument", document.getDocumentNumber(), ActionAudit.ARCHIVE, null);
        return toResponse(document);
    }

    @Transactional
    public void delete(Long id) {
        GedDocument document = getEntity(id);
        document.setStatus(GedDocumentStatus.DELETED);
        document.setDeleted(true);
        document.setUpdatedBy(authService.currentPrincipal().username());
        documentRepository.save(document);

        functionalAuditRecorder.recordAction("GED", "GedDocument", document.getDocumentNumber(), ActionAudit.DELETE, null);
    }

    // ---------------------------------------------------------------- Liens metier

    @Transactional
    public GedDocumentLinkResponse addLink(Long documentId, GedDocumentLinkRequest request) {
        GedDocument document = getEntity(documentId);
        GedDocumentLink link = createLink(document, request.entityType(), request.entityId(), request.relationshipType(),
                authService.currentPrincipal().username());
        return toLinkResponse(link);
    }

    public List<GedDocumentLinkResponse> listLinks(Long documentId) {
        getEntity(documentId);
        return linkRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .map(this::toLinkResponse)
                .toList();
    }

    @Transactional
    public void removeLink(Long documentId, Long linkId) {
        getEntity(documentId);
        GedDocumentLink link = linkRepository.findByIdAndDocumentId(linkId, documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien introuvable: " + linkId));
        linkRepository.delete(link);
    }

    // ---------------------------------------------------------------- Internes

    GedDocument getEntity(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable: " + id));
    }

    /** validate()/reject() ne sont autorises que depuis UPLOADED (etat initial) - pas de flux de
     * re-soumission dans cette phase (§21 differe, cf. plan), donc un document deja VALIDATED/
     * REJECTED/ARCHIVED/DELETED ne peut pas etre re-valide ou re-rejete. */
    private void requireUploaded(GedDocument document) {
        if (document.getStatus() != GedDocumentStatus.UPLOADED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document deja " + document.getStatus());
        }
    }

    private GedDocumentLink createLink(GedDocument document, String entityType, Long entityId, String relationshipType, String createdBy) {
        linkRepository.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream()
                .filter(existing -> existing.getEntityType().equalsIgnoreCase(entityType) && existing.getEntityId().equals(entityId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce document est deja rattache a " + entityType + " " + entityId);
                });

        GedDocumentLink link = GedDocumentLink.builder()
                .document(document)
                .entityType(entityType.toUpperCase(Locale.ROOT))
                .entityId(entityId)
                .relationshipType(StringUtils.hasText(relationshipType) ? relationshipType : "ATTACHMENT")
                .createdBy(createdBy)
                .build();
        return linkRepository.save(link);
    }

    /** Valide taille (§26, cle MAX_FILE_SIZE partagee avec Sinistre/Reclamation/Partenaire) et
     * extension (whitelist GED_ALLOWED_EXTENSIONS). N'inspecte pas les octets reels du fichier -
     * limite honnete documentee dans le plan : c'est le role futur de DocumentSecurityScanner. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        long maxFileSize = dynamicConfigurationService.getLong("MAX_FILE_SIZE", 10L * 1024 * 1024);
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier trop volumineux (" + file.getSize() + " octets, maximum autorise: " + maxFileSize + " octets, configurable via MAX_FILE_SIZE)");
        }

        String extension = extractExtension(file.getOriginalFilename());
        List<String> allowedExtensions = allowedExtensions();
        if (!StringUtils.hasText(extension) || !allowedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Extension de fichier non autorisee: ." + extension + " (autorisees: " + String.join(", ", allowedExtensions) + ", configurable via GED_ALLOWED_EXTENSIONS)");
        }

        var scanResult = documentSecurityScanner.scan(uncheckedInputStream(file));
        if (scanResult.status() == DocumentSecurityScanner.ScanStatus.INFECTED) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Fichier rejete par l'analyse de securite: " + scanResult.details());
        }
    }

    private List<String> allowedExtensions() {
        String raw = dynamicConfigurationService.getString("GED_ALLOWED_EXTENSIONS", String.join(",", DEFAULT_ALLOWED_EXTENSIONS));
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private InputStream uncheckedInputStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Structure de repertoire du §9 : {documentTypeCode}/{yyyy}/{MM}/{documentUuid}/v{version}/. */
    private StorageResult storeFile(String documentTypeCode, String documentUuid, int versionNumber, MultipartFile file) {
        LocalDate now = LocalDate.now();
        String relativeDir = documentTypeCode + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue())
                + "/" + documentUuid + "/v" + versionNumber;
        try {
            return documentStorageService.store(relativeDir, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private GedDocumentVersion saveVersion(GedDocument document, int versionNumber, MultipartFile file, StorageResult storageResult, String createdBy) {
        GedDocumentVersion version = GedDocumentVersion.builder()
                .document(document)
                .versionNumber(versionNumber)
                .fileName(storageResult.storedFileName())
                .originalFileName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document")
                .mimeType(file.getContentType())
                .extension(extractExtension(file.getOriginalFilename()))
                .fileSizeBytes(storageResult.sizeBytes())
                .checksumSha256(storageResult.checksumSha256())
                .storedPath(storageResult.storedPath())
                .createdBy(createdBy)
                .build();
        return versionRepository.save(version);
    }

    private List<GedDocumentResponse> filter(String documentNumber, String documentType, String category,
                                              GedDocumentStatus status, String entityType, Long entityId,
                                              String createdBy, LocalDate dateFrom, LocalDate dateTo,
                                              GedConfidentialityLevel confidentialityLevel) {
        String normalizedNumber = StringUtils.hasText(documentNumber) ? documentNumber.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedType = StringUtils.hasText(documentType) ? documentType.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedCategory = StringUtils.hasText(category) ? category.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedCreatedBy = StringUtils.hasText(createdBy) ? createdBy.trim() : null;
        Instant from = dateFrom != null ? dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant to = dateTo != null ? dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;

        Set<Long> entityDocumentIds = StringUtils.hasText(entityType) && entityId != null
                ? linkRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream()
                        .map(link -> link.getDocument().getId())
                        .collect(java.util.stream.Collectors.toCollection(HashSet::new))
                : null;

        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(d -> normalizedNumber == null || d.getDocumentNumber().toUpperCase(Locale.ROOT).contains(normalizedNumber))
                .filter(d -> normalizedType == null || d.getDocumentType().getCode().equalsIgnoreCase(normalizedType))
                .filter(d -> normalizedCategory == null || (d.getCategory() != null && d.getCategory().getCode().equalsIgnoreCase(normalizedCategory)))
                .filter(d -> status == null || d.getStatus() == status)
                .filter(d -> entityDocumentIds == null || entityDocumentIds.contains(d.getId()))
                .filter(d -> normalizedCreatedBy == null || normalizedCreatedBy.equalsIgnoreCase(d.getCreatedBy()))
                .filter(d -> from == null || !d.getCreatedAt().isBefore(from))
                .filter(d -> to == null || d.getCreatedAt().isBefore(to))
                .filter(d -> confidentialityLevel == null || d.getConfidentialityLevel() == confidentialityLevel)
                .map(this::toResponse)
                .toList();
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

    private GedDocumentResponse toResponse(GedDocument d) {
        Integer currentVersionNumber = versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(d.getId())
                .map(GedDocumentVersion::getVersionNumber)
                .orElse(null);
        Long duplicateOfDocumentId = StringUtils.hasText(d.getChecksumSha256())
                ? documentRepository.findFirstByChecksumSha256OrderByCreatedAtAsc(d.getChecksumSha256())
                        .map(GedDocument::getId)
                        .filter(originalId -> !originalId.equals(d.getId()))
                        .orElse(null)
                : null;

        return new GedDocumentResponse(
                d.getId(), d.getUuid(), d.getDocumentNumber(),
                d.getDocumentType().getId(), d.getDocumentType().getCode(), d.getDocumentType().getNom(),
                d.getCategory() == null ? null : d.getCategory().getId(),
                d.getCategory() == null ? null : d.getCategory().getCode(),
                d.getCategory() == null ? null : d.getCategory().getNom(),
                d.getTitle(), d.getDescription(), d.getStatus(), d.getConfidentialityLevel(), d.getSource(),
                d.getRejectionReason(), d.getChecksumSha256(), currentVersionNumber, duplicateOfDocumentId,
                d.getCreatedAt(), d.getCreatedBy(), d.getUpdatedAt(), d.getUpdatedBy(), d.getArchivedAt(), d.getArchivedBy()
        );
    }

    private GedDocumentVersionResponse toVersionResponse(GedDocumentVersion v) {
        return new GedDocumentVersionResponse(
                v.getId(), v.getVersionNumber(), v.getFileName(), v.getOriginalFileName(), v.getMimeType(),
                v.getExtension(), v.getFileSizeBytes(), v.getChecksumSha256(), v.getStorageProvider(),
                v.getCreatedAt(), v.getCreatedBy()
        );
    }

    private GedDocumentLinkResponse toLinkResponse(GedDocumentLink l) {
        return new GedDocumentLinkResponse(l.getId(), l.getEntityType(), l.getEntityId(), l.getRelationshipType(), l.getCreatedAt(), l.getCreatedBy());
    }
}
