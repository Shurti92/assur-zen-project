package ci.assurzen.services;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.TraductionRequest;
import ci.assurzen.dto.TraductionResponse;
import ci.assurzen.dto.TraductionUpdateRequest;
import ci.assurzen.entities.Langue;
import ci.assurzen.entities.TraductionEntry;
import ci.assurzen.repositories.LangueRepository;
import ci.assurzen.repositories.TraductionEntryRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Administration des entrees du dictionnaire i18n (CRUD), reserve comme le referentiel Langue lui-meme. */
@Service
@RequiredArgsConstructor
public class TraductionEntryService {

    private final TraductionEntryRepository traductionEntryRepository;
    private final LangueRepository langueRepository;
    private final AuthService authService;

    public PageResponse<TraductionResponse> list(String langueCode, String scope, Integer page, Integer size) {
        List<TraductionEntry> entrees;
        if (StringUtils.hasText(langueCode) && StringUtils.hasText(scope)) {
            entrees = traductionEntryRepository.findByLangue_CodeAndScopeOrderByCleAsc(langueCode, scope);
        } else if (StringUtils.hasText(langueCode)) {
            entrees = traductionEntryRepository.findByLangue_CodeOrderByCleAsc(langueCode);
        } else {
            entrees = traductionEntryRepository.findAll().stream()
                    .filter(e -> !StringUtils.hasText(scope) || scope.equals(e.getScope()))
                    .toList();
        }

        return paginate(entrees.stream().map(this::toResponse).toList(), page, size);
    }

    @Transactional
    public TraductionResponse create(TraductionRequest request) {
        requireWriteAccess();
        Langue langue = resolveLangue(request.langueCode());

        traductionEntryRepository.findByCleAndLangue_Code(request.cle(), request.langueCode()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une traduction existe deja pour la cle '" + request.cle() + "' en langue " + request.langueCode());
        });

        TraductionEntry entree = TraductionEntry.builder()
                .cle(request.cle())
                .scope(request.scope())
                .langue(langue)
                .valeur(request.valeur())
                .build();

        return toResponse(traductionEntryRepository.save(entree));
    }

    @Transactional
    public TraductionResponse update(Long id, TraductionUpdateRequest request) {
        requireWriteAccess();
        TraductionEntry entree = getEntity(id);
        entree.setValeur(request.valeur());
        return toResponse(traductionEntryRepository.save(entree));
    }

    @Transactional
    public void delete(Long id) {
        requireWriteAccess();
        TraductionEntry entree = getEntity(id);
        traductionEntryRepository.delete(entree);
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canManageCommunication(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / marketing / communication");
        }
    }

    private Langue resolveLangue(String langueCode) {
        return langueRepository.findByCode(langueCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Langue introuvable: " + langueCode));
    }

    private TraductionEntry getEntity(Long id) {
        return traductionEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traduction introuvable: " + id));
    }

    private TraductionResponse toResponse(TraductionEntry e) {
        return new TraductionResponse(
                e.getId(),
                e.getCle(),
                e.getScope(),
                e.getLangue() == null ? null : e.getLangue().getCode(),
                e.getValeur(),
                e.getLastUpdate()
        );
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
