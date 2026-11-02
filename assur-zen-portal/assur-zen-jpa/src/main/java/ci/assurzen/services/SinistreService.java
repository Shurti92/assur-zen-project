package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SinistrePhotoContent;
import ci.assurzen.dto.SinistrePhotoResponse;
import ci.assurzen.dto.SinistreRequest;
import ci.assurzen.dto.SinistreResponse;
import ci.assurzen.dto.SinistreSuiviRequest;
import ci.assurzen.dto.SinistreSuiviResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Compagnie;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Sinistre;
import ci.assurzen.entities.SinistrePhoto;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.entities.SinistreSuivi;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.CompagnieRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.SinistrePhotoRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.repositories.SinistreSuiviRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import static ci.assurzen.utils.SafeAccess.safe;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SinistreService {

    private final SinistreRepository sinistreRepository;
    private final SinistreSuiviRepository sinistreSuiviRepository;
    private final SinistrePhotoRepository sinistrePhotoRepository;
    private final ContractRepository contractRepository;
    private final CompagnieRepository compagnieRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final SmsNotificationService smsNotificationService;
    private final SinistrePhotoStorageService sinistrePhotoStorageService;
    private final BusinessMetrics businessMetrics;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final DynamicConfigurationService dynamicConfigurationService;

    public PageResponse<SinistreResponse> search(String q, SinistreStatus status, Long contractId, Long insuredId,
                                                  Integer page, Integer size) {
        return paginate(filter(q, status, contractId, insuredId), page, size);
    }

    public SinistreResponse getSinistre(Long id) {
        return toResponse(getScoped(id));
    }

    /**
     * Historique complet, non filtre par la portee du commercial connecte, de tous les sinistres
     * jamais declares par un assure (depuis le debut). Reserve aux usages back-office (ex. fiche de
     * sinistralite) qui ont besoin d'une vue exhaustive, pas de la vue scopee de search().
     */
    public List<SinistreResponse> findAllByInsuredId(Long insuredId) {
        return sinistreRepository.findByInsuredIdOrderByCreatedAtDesc(insuredId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SinistreSuiviResponse> listSuivis(Long id) {
        getScoped(id);
        return sinistreSuiviRepository.findBySinistreIdOrderByCreatedAtAsc(id)
                .stream()
                .map(this::toSuiviResponse)
                .toList();
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "SINISTRES", entityName = "Sinistre")
    public SinistreResponse create(SinistreRequest request) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);

        Contract contract = resolveContract(request.contractId());
        if (!admin && (contract.getSalesRep() == null || !contract.getSalesRep().getId().equals(principal.userId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + request.contractId());
        }

        User declarant = (admin && request.declarantId() != null)
                ? resolveUser(request.declarantId())
                : resolveUser(principal.userId());

        String numero = codeGeneratorService.generate(CodePrefix.SINISTRE);

        Compagnie compagnie = request.compagnieId() != null
                ? resolveCompagnie(request.compagnieId())
                : contract.getCompagnie();

        Sinistre sinistre = Sinistre.builder()
                .numero(numero)
                .contract(contract)
                .insured(contract.getInsured())
                .nature(request.nature())
                .dateSurvenance(request.dateSurvenance())
                .dateDeclaration(LocalDate.now())
                .montantEstime(request.montantEstime())
                .status(SinistreStatus.declare)
                .declarant(declarant)
                .assignee(request.assigneeId() != null ? resolveUser(request.assigneeId()) : null)
                .compagnie(compagnie)
                .gestionnaireNom(request.gestionnaireNom())
                .description(request.description())
                .notes(request.notes())
                .build();
        sinistre = sinistreRepository.save(sinistre);

        String phone = contract.getInsured() == null ? null : safe(contract.getInsured()::getClientPhone);
        boolean notified = smsNotificationService.sendClaimDeclaredSms(phone, numero);

        SinistreSuivi suivi = SinistreSuivi.builder()
                .sinistre(sinistre)
                .statut(SinistreStatus.declare)
                .commentaire("Sinistre declare")
                .auteur(declarant)
                .notificationEnvoyee(notified)
                .build();
        sinistreSuiviRepository.save(suivi);

        businessMetrics.incrementClaimsDeclared();

        return toResponse(sinistre);
    }

    @Transactional
    public SinistreResponse update(Long id, SinistreRequest request) {
        Sinistre sinistre = getScoped(id);
        SinistreResponse avant = toResponse(sinistre);

        sinistre.setContract(resolveContract(request.contractId()));
        sinistre.setInsured(sinistre.getContract().getInsured());
        sinistre.setNature(request.nature());
        sinistre.setDateSurvenance(request.dateSurvenance());
        sinistre.setMontantEstime(request.montantEstime());
        sinistre.setGestionnaireNom(request.gestionnaireNom());
        sinistre.setDescription(request.description());
        sinistre.setNotes(request.notes());
        if (request.assigneeId() != null) {
            sinistre.setAssignee(resolveUser(request.assigneeId()));
        }
        if (request.compagnieId() != null) {
            sinistre.setCompagnie(resolveCompagnie(request.compagnieId()));
        }

        SinistreResponse apres = toResponse(sinistreRepository.save(sinistre));
        functionalAuditRecorder.recordUpdate("SINISTRES", "Sinistre", String.valueOf(id), avant, apres, request.notes());
        return apres;
    }

    @Transactional
    public SinistreSuiviResponse addSuivi(Long id, SinistreSuiviRequest request) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        Sinistre sinistre = getScoped(id);
        User auteur = resolveUser(principal.userId());
        SinistreResponse avant = toResponse(sinistre);

        sinistre.setStatus(request.statut());
        if (request.montantIndemnise() != null) {
            sinistre.setMontantIndemnise(request.montantIndemnise());
        }
        sinistreRepository.save(sinistre);
        functionalAuditRecorder.recordUpdate("SINISTRES", "Sinistre", String.valueOf(id), avant, toResponse(sinistre), request.commentaire());

        String phone = sinistre.getInsured() == null ? null : safe(sinistre.getInsured()::getClientPhone);
        boolean notified = smsNotificationService.sendClaimStatusUpdateSms(phone, sinistre.getNumero(), request.statut(), request.commentaire());

        SinistreSuivi suivi = SinistreSuivi.builder()
                .sinistre(sinistre)
                .statut(request.statut())
                .commentaire(request.commentaire())
                .auteur(auteur)
                .notificationEnvoyee(notified)
                .build();
        suivi = sinistreSuiviRepository.save(suivi);

        return toSuiviResponse(suivi);
    }

    public List<SinistrePhotoResponse> listPhotos(Long id) {
        getScoped(id);
        return sinistrePhotoRepository.findBySinistreIdOrderByCreatedAtAsc(id)
                .stream()
                .map(this::toPhotoResponse)
                .toList();
    }

    @Transactional
    public SinistrePhotoResponse uploadPhoto(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        long maxFileSize = dynamicConfigurationService.getLong("MAX_FILE_SIZE", 10L * 1024 * 1024);
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier trop volumineux (" + file.getSize() + " octets, maximum autorise: " + maxFileSize + " octets, configurable via MAX_FILE_SIZE)");
        }
        Sinistre sinistre = getScoped(id);
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();

        String storedPath;
        try {
            storedPath = sinistrePhotoStorageService.store(id, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        SinistrePhoto photo = SinistrePhoto.builder()
                .sinistre(sinistre)
                .fileName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "photo")
                .storedPath(storedPath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedBy(resolveUser(principal.userId()))
                .build();

        return toPhotoResponse(sinistrePhotoRepository.save(photo));
    }

    public SinistrePhotoContent getPhotoContent(Long id, Long photoId) {
        getScoped(id);
        SinistrePhoto photo = sinistrePhotoRepository.findByIdAndSinistreId(photoId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo introuvable: " + photoId));
        return new SinistrePhotoContent(sinistrePhotoStorageService.read(photo.getStoredPath()), photo.getContentType(), photo.getFileName());
    }

    private Sinistre getScoped(Long id) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return sinistreRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinistre introuvable: " + id));
        }
        return sinistreRepository.findByIdAndContract_SalesRep_Id(id, principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinistre introuvable: " + id));
    }

    private List<SinistreResponse> filter(String q, SinistreStatus status, Long contractId, Long insuredId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        return sinistreRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(sinistre -> admin || (sinistre.getContract() != null && sinistre.getContract().getSalesRep() != null
                        && sinistre.getContract().getSalesRep().getId().equals(principal.userId())))
                .filter(sinistre -> status == null || sinistre.getStatus() == status)
                .filter(sinistre -> contractId == null || (sinistre.getContract() != null && contractId.equals(sinistre.getContract().getId())))
                .filter(sinistre -> insuredId == null || (sinistre.getInsured() != null && insuredId.equals(sinistre.getInsured().getId())))
                .filter(sinistre -> normalizedQuery == null || matchesQuery(sinistre, normalizedQuery))
                .map(this::toResponse)
                .toList();
    }

    private boolean matchesQuery(Sinistre sinistre, String normalizedQuery) {
        return contains(sinistre.getNumero(), normalizedQuery)
                || contains(sinistre.getNature(), normalizedQuery)
                || contains(insuredName(sinistre), normalizedQuery)
                || contains(displayName(sinistre.getDeclarant()), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
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

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private Contract resolveContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + userId));
    }

    private Compagnie resolveCompagnie(Long compagnieId) {
        return compagnieRepository.findById(compagnieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compagnie introuvable: " + compagnieId));
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElseGet(() -> safe(user::getUsername));
    }

    private String insuredName(Sinistre sinistre) {
        if (sinistre.getInsured() == null) {
            return null;
        }
        return safe(() -> sinistre.getInsured().getPrenoms() + " " + sinistre.getInsured().getNom());
    }

    private SinistreResponse toResponse(Sinistre sinistre) {
        return new SinistreResponse(
                sinistre.getId(),
                sinistre.getNumero(),
                sinistre.getContract() == null ? null : safe(sinistre.getContract()::getId),
                sinistre.getContract() == null ? null : safe(sinistre.getContract()::getPolicyNumber),
                sinistre.getInsured() == null ? null : safe(sinistre.getInsured()::getId),
                insuredName(sinistre),
                sinistre.getNature(),
                sinistre.getDateSurvenance(),
                sinistre.getDateDeclaration(),
                sinistre.getMontantEstime(),
                sinistre.getMontantIndemnise(),
                sinistre.getStatus(),
                sinistre.getDeclarant() == null ? null : safe(sinistre.getDeclarant()::getId),
                displayName(sinistre.getDeclarant()),
                sinistre.getAssignee() == null ? null : safe(sinistre.getAssignee()::getId),
                sinistre.getAssignee() == null ? null : displayName(sinistre.getAssignee()),
                sinistre.getCompagnie() == null ? null : safe(sinistre.getCompagnie()::getId),
                sinistre.getCompagnie() == null ? null : safe(sinistre.getCompagnie()::getNom),
                sinistre.getGestionnaireNom(),
                sinistre.getDescription(),
                sinistre.getNotes(),
                sinistre.getCreatedAt()
        );
    }

    private SinistrePhotoResponse toPhotoResponse(SinistrePhoto photo) {
        return new SinistrePhotoResponse(
                photo.getId(),
                photo.getSinistre() == null ? null : photo.getSinistre().getId(),
                photo.getFileName(),
                photo.getContentType(),
                photo.getSizeBytes(),
                displayName(photo.getUploadedBy()),
                photo.getCreatedAt()
        );
    }

    private SinistreSuiviResponse toSuiviResponse(SinistreSuivi suivi) {
        return new SinistreSuiviResponse(
                suivi.getId(),
                suivi.getSinistre() == null ? null : suivi.getSinistre().getId(),
                suivi.getStatut(),
                suivi.getCommentaire(),
                suivi.getAuteur() == null ? null : safe(suivi.getAuteur()::getId),
                displayName(suivi.getAuteur()),
                suivi.isNotificationEnvoyee(),
                suivi.getCreatedAt()
        );
    }
}
