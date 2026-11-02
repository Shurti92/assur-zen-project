package ci.assurzen.services;

import ci.assurzen.dto.EnqueteReponseRequest;
import ci.assurzen.dto.EnqueteSatisfactionRequest;
import ci.assurzen.dto.EnqueteSatisfactionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.EnqueteContexte;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Sinistre;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.mapper.EnqueteSatisfactionMapper;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.EnqueteSatisfactionRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SatisfactionService {

    private final EnqueteSatisfactionRepository enqueteSatisfactionRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final SinistreRepository sinistreRepository;
    private final AuthService authService;
    private final CodeGeneratorService codeGeneratorService;
    private final EnqueteSatisfactionMapper enqueteSatisfactionMapper;
    private final SmsNotificationService smsNotificationService;
    private final ReclamationService reclamationService;

    public PageResponse<EnqueteSatisfactionResponse> search(EnqueteStatut statut, EnqueteContexte contexte, Long insuredId,
                                                              Integer page, Integer size) {
        List<EnqueteSatisfactionResponse> items = filter(statut, contexte, insuredId);
        return paginate(items, page, size);
    }

    public EnqueteSatisfactionResponse get(Long id) {
        return enqueteSatisfactionMapper.toResponse(getScoped(id));
    }

    @Transactional
    public EnqueteSatisfactionResponse envoyer(EnqueteSatisfactionRequest request) {
        Insured insured = insuredRepository.findById(request.insuredId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + request.insuredId()));
        checkAccess(insured);

        Contract contrat = null;
        if (request.contratId() != null) {
            contrat = contractRepository.findById(request.contratId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + request.contratId()));
        }
        Sinistre sinistre = null;
        if (request.sinistreId() != null) {
            sinistre = sinistreRepository.findById(request.sinistreId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinistre introuvable: " + request.sinistreId()));
        }

        EnqueteSatisfaction enquete = EnqueteSatisfaction.builder()
                .code(codeGeneratorService.generate(CodePrefix.ENQUETE))
                .contexte(request.contexte())
                .insured(insured)
                .contrat(contrat)
                .sinistre(sinistre)
                .dateEnvoi(Instant.now())
                .statut(EnqueteStatut.ENVOYEE)
                .build();
        enquete = enqueteSatisfactionRepository.save(enquete);

        boolean notifie = smsNotificationService.sendSatisfactionSurveySms(insured.getClientPhone(), enquete.getCode());
        enquete.setStatut(notifie ? EnqueteStatut.ENVOYEE : EnqueteStatut.ECHEC_ENVOI);
        enquete = enqueteSatisfactionRepository.save(enquete);

        return enqueteSatisfactionMapper.toResponse(enquete);
    }

    @Transactional
    public EnqueteSatisfactionResponse repondre(Long id, EnqueteReponseRequest request) {
        EnqueteSatisfaction enquete = getScoped(id);
        if (enquete.getStatut() == EnqueteStatut.REPONDUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette enquete a deja recu une reponse");
        }

        enquete.setScoreNps(request.scoreNps());
        enquete.setCommentaire(request.commentaire());
        enquete.setDateReponse(Instant.now());
        enquete.setStatut(EnqueteStatut.REPONDUE);
        enquete = enqueteSatisfactionRepository.save(enquete);

        reclamationService.creerAutomatiquementDepuisEnquete(enquete, request.scoreNps(), request.commentaire());

        return enqueteSatisfactionMapper.toResponse(enquete);
    }

    private List<EnqueteSatisfactionResponse> filter(EnqueteStatut statut, EnqueteContexte contexte, Long insuredId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean canViewAll = FidelisationRoles.canViewAll(principal);

        return enqueteSatisfactionRepository.findAll().stream()
                .filter(e -> canViewAll || estAccessible(e.getInsured(), principal))
                .filter(e -> statut == null || e.getStatut() == statut)
                .filter(e -> contexte == null || e.getContexte() == contexte)
                .filter(e -> insuredId == null || (e.getInsured() != null && insuredId.equals(e.getInsured().getId())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(enqueteSatisfactionMapper::toResponse)
                .toList();
    }

    private EnqueteSatisfaction getScoped(Long id) {
        EnqueteSatisfaction enquete = enqueteSatisfactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquete introuvable: " + id));
        checkAccess(enquete.getInsured());
        return enquete;
    }

    private void checkAccess(Insured insured) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (FidelisationRoles.canViewAll(principal) || estAccessible(insured, principal)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + (insured == null ? null : insured.getId()));
    }

    private boolean estAccessible(Insured insured, AuthenticatedUserPrincipal principal) {
        if (insured == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .anyMatch(c -> c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId()));
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
