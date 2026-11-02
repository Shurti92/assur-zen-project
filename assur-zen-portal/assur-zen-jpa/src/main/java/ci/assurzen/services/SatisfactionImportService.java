package ci.assurzen.services;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SatisfactionImportErrorResponse;
import ci.assurzen.dto.SatisfactionImportReportResponse;
import ci.assurzen.dto.SatisfactionImportResponse;
import ci.assurzen.entities.DuplicateStrategy;
import ci.assurzen.entities.ImportErrorType;
import ci.assurzen.entities.SatisfactionImport;
import ci.assurzen.entities.SatisfactionImportError;
import ci.assurzen.entities.SatisfactionImportMappingProfile;
import ci.assurzen.entities.SatisfactionImportStatut;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.mapper.SatisfactionImportErrorMapper;
import ci.assurzen.mapper.SatisfactionImportMapper;
import ci.assurzen.repositories.SatisfactionImportErrorRepository;
import ci.assurzen.repositories.SatisfactionImportRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SatisfactionImportService {

    private final SatisfactionImportRepository satisfactionImportRepository;
    private final SatisfactionImportErrorRepository satisfactionImportErrorRepository;
    private final SatisfactionImportMappingProfileService mappingProfileService;
    private final SatisfactionImportStorageService satisfactionImportStorageService;
    private final CsvImportEngine csvImportEngine;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final CodeGeneratorService codeGeneratorService;
    private final SatisfactionImportMapper satisfactionImportMapper;
    private final SatisfactionImportErrorMapper satisfactionImportErrorMapper;

    public PageResponse<SatisfactionImportResponse> search(SatisfactionImportStatut statut, Long mappingProfileId, String source,
                                                             String surveyId, Long executeParId, Instant dateDebut, Instant dateFin,
                                                             Integer page, Integer size) {
        List<SatisfactionImportResponse> items = satisfactionImportRepository.findAll().stream()
                .filter(i -> statut == null || i.getStatut() == statut)
                .filter(i -> mappingProfileId == null || (i.getMappingProfile() != null && mappingProfileId.equals(i.getMappingProfile().getId())))
                .filter(i -> source == null || source.equalsIgnoreCase(i.getSource()))
                .filter(i -> surveyId == null || surveyId.equals(i.getSurveyId()))
                .filter(i -> executeParId == null || (i.getExecutePar() != null && executeParId.equals(i.getExecutePar().getId())))
                .filter(i -> dateDebut == null || !i.getCreatedAt().isBefore(dateDebut))
                .filter(i -> dateFin == null || !i.getCreatedAt().isAfter(dateFin))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(satisfactionImportMapper::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public SatisfactionImportResponse get(Long id) {
        return satisfactionImportMapper.toResponse(getEntity(id));
    }

    @Transactional
    public SatisfactionImportResponse upload(MultipartFile file, Long mappingProfileId, String surveyId, String source,
                                              String delimiter, String encoding, Boolean hasHeader,
                                              DuplicateStrategy duplicateStrategy, Boolean executeImmediately) {
        requireWriteAccess();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV manquant ou vide");
        }

        SatisfactionImportMappingProfile profile = mappingProfileService.getEntity(mappingProfileId);
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User executePar = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        String code = codeGeneratorService.generate(CodePrefix.IMPORT_SATISFACTION);
        String storedPath;
        try {
            storedPath = satisfactionImportStorageService.store(code, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        SatisfactionImport imp = SatisfactionImport.builder()
                .code(code)
                .mappingProfile(profile)
                .source(source)
                .surveyId(surveyId)
                .fileName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "import.csv")
                .storedFilePath(storedPath)
                .delimiter(StringUtils.hasText(delimiter) ? delimiter : ";")
                .encoding(StringUtils.hasText(encoding) ? encoding : "UTF-8")
                .hasHeader(hasHeader == null || hasHeader)
                .duplicateStrategy(duplicateStrategy == null ? DuplicateStrategy.IGNORER : duplicateStrategy)
                .statut(SatisfactionImportStatut.PENDING)
                .executePar(executePar)
                .build();
        stampCreated(imp);
        imp = satisfactionImportRepository.save(imp);

        if (executeImmediately == null || executeImmediately) {
            declencherApresCommit(imp.getId());
        }

        return satisfactionImportMapper.toResponse(imp);
    }

    @Transactional
    public SatisfactionImportResponse retry(Long id) {
        requireWriteAccess();
        SatisfactionImport imp = getEntity(id);
        if (imp.getStatut() == SatisfactionImportStatut.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet import est deja en cours de traitement");
        }
        imp.setStatut(SatisfactionImportStatut.PENDING);
        stampUpdated(imp);
        imp = satisfactionImportRepository.save(imp);

        declencherApresCommit(imp.getId());

        return satisfactionImportMapper.toResponse(imp);
    }

    @Transactional
    public void delete(Long id) {
        requireWriteAccess();
        SatisfactionImport imp = getEntity(id);
        if (imp.getStatut() == SatisfactionImportStatut.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de supprimer un import en cours de traitement");
        }
        imp.setDeleted(true);
        stampUpdated(imp);
        satisfactionImportRepository.save(imp);
    }

    public List<SatisfactionImportErrorResponse> listErrors(Long id) {
        getEntity(id);
        return satisfactionImportErrorRepository.findBySatisfactionImportIdOrderByLigneNumeroAsc(id).stream()
                .map(satisfactionImportErrorMapper::toResponse)
                .toList();
    }

    public SatisfactionImportReportResponse report(Long id) {
        SatisfactionImport imp = getEntity(id);
        List<SatisfactionImportError> erreurs = satisfactionImportErrorRepository.findBySatisfactionImportIdOrderByLigneNumeroAsc(id);
        Map<ImportErrorType, Long> parType = erreurs.stream()
                .collect(Collectors.groupingBy(SatisfactionImportError::getTypeErreur, Collectors.counting()));

        int total = orZero(imp.getTotalLignes());
        int reussies = orZero(imp.getLignesReussies());
        double taux = total == 0 ? 0d : (reussies * 100.0) / total;

        return new SatisfactionImportReportResponse(
                imp.getId(),
                imp.getCode(),
                imp.getStatut(),
                total,
                orZero(imp.getLignesIgnorees()),
                reussies,
                orZero(imp.getLignesEnErreur()),
                taux,
                imp.getPromoteurs() == null ? 0 : imp.getPromoteurs(),
                imp.getPassifs() == null ? 0 : imp.getPassifs(),
                imp.getDetracteurs() == null ? 0 : imp.getDetracteurs(),
                imp.getScoreNpsMoyen(),
                parType
        );
    }

    private void declencherApresCommit(Long importId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                csvImportEngine.executer(importId);
            }
        });
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private SatisfactionImport getEntity(Long id) {
        return satisfactionImportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable: " + id));
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageSatisfactionImports(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / gestionnaires satisfaction");
        }
    }

    private void stampCreated(SatisfactionImport entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(SatisfactionImport entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
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
}
