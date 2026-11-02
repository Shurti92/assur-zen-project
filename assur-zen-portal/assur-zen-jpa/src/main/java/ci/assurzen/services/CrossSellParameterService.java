package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CrossSellParameterRequest;
import ci.assurzen.dto.CrossSellParameterResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CrossSellParameter;
import ci.assurzen.repositories.CrossSellParameterRepository;
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
public class CrossSellParameterService {

    private final CrossSellParameterRepository crossSellParameterRepository;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<CrossSellParameterResponse> list() {
        return crossSellParameterRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CrossSellParameterResponse upsert(CrossSellParameterRequest request) {
        requireCanManage();
        CrossSellParameter existant = crossSellParameterRepository.findByCle(request.cle()).orElse(null);
        CrossSellParameterResponse avant = existant != null ? toResponse(existant) : null;
        CrossSellParameter parametre = existant != null ? existant : CrossSellParameter.builder().cle(request.cle()).build();

        parametre.setValeur(request.valeur());
        parametre.setDescription(request.description());

        CrossSellParameterResponse apres = toResponse(crossSellParameterRepository.save(parametre));
        if (avant == null) {
            functionalAuditRecorder.recordAction("CROSS_SELL", "CrossSellParameter", String.valueOf(apres.id()), ActionAudit.CREATE, null);
        } else {
            functionalAuditRecorder.recordUpdate("CROSS_SELL", "CrossSellParameter", String.valueOf(apres.id()), avant, apres, null);
        }
        return apres;
    }

    @Transactional
    public void delete(Long id) {
        requireCanManage();
        CrossSellParameter parametre = crossSellParameterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parametre introuvable: " + id));
        crossSellParameterRepository.delete(parametre);
        functionalAuditRecorder.recordAction("CROSS_SELL", "CrossSellParameter", String.valueOf(id), ActionAudit.DELETE, null);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private CrossSellParameterResponse toResponse(CrossSellParameter p) {
        return new CrossSellParameterResponse(p.getId(), p.getCle(), p.getValeur(), p.getDescription());
    }
}
