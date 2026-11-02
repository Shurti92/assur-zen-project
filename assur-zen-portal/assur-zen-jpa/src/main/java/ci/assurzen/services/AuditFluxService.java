package ci.assurzen.services;

import ci.assurzen.dto.AuditFluxResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.AuditFlux;
import ci.assurzen.repositories.AuditFluxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditFluxService {

    private final AuditFluxRepository auditFluxRepository;

    public PageResponse<AuditFluxResponse> list(Integer page, Integer size) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? 20 : Math.max(size, 1);

        Page<AuditFlux> result = auditFluxRepository.findAll(
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "creation")));

        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private AuditFluxResponse toResponse(AuditFlux entity) {
        return new AuditFluxResponse(
                entity.getId(),
                entity.getCode(),
                entity.getCreation(),
                entity.getOperation(),
                entity.getFlux(),
                entity.getExceptionMessage(),
                entity.getDirection(),
                entity.getUsername(),
                entity.getNumeroContrat(),
                entity.getRoute(),
                entity.getStatusCode(),
                entity.getIpAddress(),
                entity.getUserId(),
                entity.getDurationMs());
    }
}
