package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.SatisfactionImportMappingProfileRequest;
import ci.assurzen.dto.SatisfactionImportMappingProfileResponse;
import ci.assurzen.dto.SatisfactionImportMappingRequest;
import ci.assurzen.dto.SatisfactionImportMappingResponse;
import ci.assurzen.dto.SatisfactionImportMappingValueRequest;
import ci.assurzen.dto.SatisfactionImportMappingValueResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.SatisfactionImportMapping;
import ci.assurzen.entities.SatisfactionImportMappingProfile;
import ci.assurzen.entities.SatisfactionImportMappingValue;
import ci.assurzen.mapper.SatisfactionImportMappingMapper;
import ci.assurzen.mapper.SatisfactionImportMappingProfileMapper;
import ci.assurzen.mapper.SatisfactionImportMappingValueMapper;
import ci.assurzen.repositories.SatisfactionImportMappingProfileRepository;
import ci.assurzen.repositories.SatisfactionImportMappingRepository;
import ci.assurzen.repositories.SatisfactionImportMappingValueRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SatisfactionImportMappingProfileService {

    private final SatisfactionImportMappingProfileRepository profileRepository;
    private final SatisfactionImportMappingRepository mappingRepository;
    private final SatisfactionImportMappingValueRepository mappingValueRepository;
    private final AuthService authService;
    private final SatisfactionImportMappingProfileMapper profileMapper;
    private final SatisfactionImportMappingValueMapper mappingValueMapper;
    private final SatisfactionImportMappingMapper mappingMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<SatisfactionImportMappingProfileResponse> list() {
        return profileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    public SatisfactionImportMappingProfileResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "SATISFACTION_IMPORT", entityName = "SatisfactionImportMappingProfile")
    public SatisfactionImportMappingProfileResponse create(SatisfactionImportMappingProfileRequest request) {
        requireWriteAccess();
        profileRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un profil de mapping existe deja pour ce code: " + request.code());
        });

        SatisfactionImportMappingProfile profile = SatisfactionImportMappingProfile.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .active(request.active() == null || request.active())
                .build();
        stampCreated(profile);

        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public SatisfactionImportMappingProfileResponse update(Long id, SatisfactionImportMappingProfileRequest request) {
        requireWriteAccess();
        SatisfactionImportMappingProfile profile = getEntity(id);
        SatisfactionImportMappingProfileResponse avant = toResponse(profile);

        profileRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un profil de mapping existe deja pour ce code: " + request.code());
                });

        profile.setCode(request.code());
        profile.setNom(request.nom());
        profile.setDescription(request.description());
        if (request.active() != null) {
            profile.setActive(request.active());
        }
        stampUpdated(profile);

        SatisfactionImportMappingProfileResponse apres = toResponse(profileRepository.save(profile));
        functionalAuditRecorder.recordUpdate("SATISFACTION_IMPORT", "SatisfactionImportMappingProfile", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "SATISFACTION_IMPORT", entityName = "SatisfactionImportMappingProfile")
    public void delete(Long id) {
        requireWriteAccess();
        SatisfactionImportMappingProfile profile = getEntity(id);
        profile.setDeleted(true);
        stampUpdated(profile);
        profileRepository.save(profile);
    }

    public List<SatisfactionImportMappingResponse> listMappings(Long profileId) {
        getEntity(profileId);
        return mappingRepository.findByProfileIdOrderByOrdreAsc(profileId).stream()
                .map(mappingMapper::toResponse)
                .toList();
    }

    @Transactional
    public SatisfactionImportMappingResponse addMapping(Long profileId, SatisfactionImportMappingRequest request) {
        requireWriteAccess();
        SatisfactionImportMappingProfile profile = getEntity(profileId);

        SatisfactionImportMapping mapping = SatisfactionImportMapping.builder()
                .profile(profile)
                .csvColumnName(request.csvColumnName())
                .targetField(request.targetField())
                .obligatoire(request.obligatoire() != null && request.obligatoire())
                .ordre(request.ordre() == null ? 0 : request.ordre())
                .build();
        stampCreated(mapping);

        return mappingMapper.toResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public SatisfactionImportMappingResponse updateMapping(Long profileId, Long mappingId, SatisfactionImportMappingRequest request) {
        requireWriteAccess();
        getEntity(profileId);
        SatisfactionImportMapping mapping = mappingRepository.findByIdAndProfileId(mappingId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping introuvable: " + mappingId));

        mapping.setCsvColumnName(request.csvColumnName());
        mapping.setTargetField(request.targetField());
        if (request.obligatoire() != null) {
            mapping.setObligatoire(request.obligatoire());
        }
        if (request.ordre() != null) {
            mapping.setOrdre(request.ordre());
        }
        stampUpdated(mapping);

        return mappingMapper.toResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public void deleteMapping(Long profileId, Long mappingId) {
        requireWriteAccess();
        getEntity(profileId);
        SatisfactionImportMapping mapping = mappingRepository.findByIdAndProfileId(mappingId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping introuvable: " + mappingId));
        mapping.setDeleted(true);
        stampUpdated(mapping);
        mappingRepository.save(mapping);
    }

    public List<SatisfactionImportMappingValueResponse> listMappingValues(Long profileId, Long mappingId) {
        getMappingScoped(profileId, mappingId);
        return mappingValueRepository.findByMappingId(mappingId).stream()
                .map(mappingValueMapper::toResponse)
                .toList();
    }

    @Transactional
    public SatisfactionImportMappingValueResponse addMappingValue(Long profileId, Long mappingId, SatisfactionImportMappingValueRequest request) {
        requireWriteAccess();
        SatisfactionImportMapping mapping = getMappingScoped(profileId, mappingId);

        SatisfactionImportMappingValue value = SatisfactionImportMappingValue.builder()
                .mapping(mapping)
                .valeurSource(request.valeurSource())
                .valeurCible(request.valeurCible())
                .build();
        stampCreated(value);

        return mappingValueMapper.toResponse(mappingValueRepository.save(value));
    }

    @Transactional
    public SatisfactionImportMappingValueResponse updateMappingValue(Long profileId, Long mappingId, Long valueId,
                                                                       SatisfactionImportMappingValueRequest request) {
        requireWriteAccess();
        getMappingScoped(profileId, mappingId);
        SatisfactionImportMappingValue value = mappingValueRepository.findByIdAndMappingId(valueId, mappingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Correspondance introuvable: " + valueId));

        value.setValeurSource(request.valeurSource());
        value.setValeurCible(request.valeurCible());
        stampUpdated(value);

        return mappingValueMapper.toResponse(mappingValueRepository.save(value));
    }

    @Transactional
    public void deleteMappingValue(Long profileId, Long mappingId, Long valueId) {
        requireWriteAccess();
        getMappingScoped(profileId, mappingId);
        SatisfactionImportMappingValue value = mappingValueRepository.findByIdAndMappingId(valueId, mappingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Correspondance introuvable: " + valueId));
        value.setDeleted(true);
        stampUpdated(value);
        mappingValueRepository.save(value);
    }

    private SatisfactionImportMapping getMappingScoped(Long profileId, Long mappingId) {
        getEntity(profileId);
        return mappingRepository.findByIdAndProfileId(mappingId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping introuvable: " + mappingId));
    }

    private void stampCreated(SatisfactionImportMappingValue entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(SatisfactionImportMappingValue entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }

    SatisfactionImportMappingProfile getEntity(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil de mapping introuvable: " + id));
    }

    private SatisfactionImportMappingProfileResponse toResponse(SatisfactionImportMappingProfile profile) {
        long nbMappings = mappingRepository.findByProfileIdOrderByOrdreAsc(profile.getId()).size();
        return profileMapper.toResponse(profile, nbMappings);
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageSatisfactionImports(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / gestionnaires satisfaction");
        }
    }

    private void stampCreated(SatisfactionImportMappingProfile entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(SatisfactionImportMappingProfile entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }

    private void stampCreated(SatisfactionImportMapping entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(SatisfactionImportMapping entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }
}
