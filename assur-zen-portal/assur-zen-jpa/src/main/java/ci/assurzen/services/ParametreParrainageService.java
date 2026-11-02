package ci.assurzen.services;

import ci.assurzen.dto.ParametreParrainageRequest;
import ci.assurzen.dto.ParametreParrainageResponse;
import ci.assurzen.entities.ParametreParrainage;
import ci.assurzen.repositories.ParametreParrainageRepository;
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
public class ParametreParrainageService {

    private final ParametreParrainageRepository parametreParrainageRepository;
    private final AuthService authService;

    public List<ParametreParrainageResponse> list() {
        return parametreParrainageRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ParametreParrainageResponse upsert(ParametreParrainageRequest request) {
        requireCanManage();
        ParametreParrainage parametre = parametreParrainageRepository.findByCle(request.cle())
                .orElseGet(() -> ParametreParrainage.builder().cle(request.cle()).build());

        parametre.setValeur(request.valeur());
        parametre.setDescription(request.description());

        return toResponse(parametreParrainageRepository.save(parametre));
    }

    @Transactional
    public void delete(Long id) {
        requireCanManage();
        ParametreParrainage parametre = parametreParrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parametre introuvable: " + id));
        parametreParrainageRepository.delete(parametre);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private ParametreParrainageResponse toResponse(ParametreParrainage p) {
        return new ParametreParrainageResponse(p.getId(), p.getCle(), p.getValeur(), p.getDescription());
    }
}
