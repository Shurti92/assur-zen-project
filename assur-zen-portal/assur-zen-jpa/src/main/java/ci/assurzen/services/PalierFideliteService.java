package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PalierFideliteRequest;
import ci.assurzen.dto.PalierFideliteResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.mapper.PalierFideliteMapper;
import ci.assurzen.repositories.PalierFideliteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PalierFideliteService {

    private final PalierFideliteRepository palierFideliteRepository;
    private final PalierFideliteMapper palierFideliteMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<PalierFideliteResponse> list() {
        return palierFideliteRepository.findByActiveTrueOrderBySeuilPointsAsc().stream()
                .map(palierFideliteMapper::toResponse)
                .toList();
    }

    public PalierFideliteResponse get(Long id) {
        return palierFideliteMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "PalierFidelite")
    public PalierFideliteResponse create(PalierFideliteRequest request) {
        palierFideliteRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un palier existe deja pour ce code: " + request.code());
        });

        PalierFidelite palier = PalierFidelite.builder()
                .code(request.code())
                .libelle(request.libelle())
                .seuilPoints(request.seuilPoints())
                .avantages(request.avantages())
                .couleur(request.couleur())
                .icone(request.icone())
                .estVip(request.estVip() != null && request.estVip())
                .active(request.active() == null || request.active())
                .build();

        return palierFideliteMapper.toResponse(palierFideliteRepository.save(palier));
    }

    @Transactional
    public PalierFideliteResponse update(Long id, PalierFideliteRequest request) {
        PalierFidelite palier = getEntity(id);
        PalierFideliteResponse avant = palierFideliteMapper.toResponse(palier);

        palierFideliteRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un palier existe deja pour ce code: " + request.code());
                });

        palier.setCode(request.code());
        palier.setLibelle(request.libelle());
        palier.setSeuilPoints(request.seuilPoints());
        palier.setAvantages(request.avantages());
        palier.setCouleur(request.couleur());
        palier.setIcone(request.icone());
        if (request.estVip() != null) {
            palier.setEstVip(request.estVip());
        }
        if (request.active() != null) {
            palier.setActive(request.active());
        }

        PalierFideliteResponse apres = palierFideliteMapper.toResponse(palierFideliteRepository.save(palier));
        functionalAuditRecorder.recordUpdate("FIDELITE", "PalierFidelite", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FIDELITE", entityName = "PalierFidelite")
    public void delete(Long id) {
        PalierFidelite palier = getEntity(id);
        palier.setActive(false);
        palierFideliteRepository.save(palier);
    }

    private PalierFidelite getEntity(Long id) {
        return palierFideliteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Palier de fidelite introuvable: " + id));
    }
}
