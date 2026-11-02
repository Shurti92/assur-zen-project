package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CampagneDestinataireResponse;
import ci.assurzen.dto.CampagneLancementRequest;
import ci.assurzen.dto.CampagneMarketingRequest;
import ci.assurzen.dto.CampagneMarketingResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CampagneCible;
import ci.assurzen.entities.CampagneDestinataire;
import ci.assurzen.entities.CampagneMarketing;
import ci.assurzen.entities.CampagneStatut;
import ci.assurzen.entities.CampagneType;
import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.CommunicationTemplate;
import ci.assurzen.entities.CompteFidelite;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Segment;
import ci.assurzen.entities.StatutEnvoiDestinataire;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.mapper.CampagneDestinataireMapper;
import ci.assurzen.mapper.CampagneMarketingMapper;
import ci.assurzen.repositories.CampagneDestinataireRepository;
import ci.assurzen.repositories.CampagneMarketingRepository;
import ci.assurzen.repositories.CommunicationTemplateRepository;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.SegmentRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CampagneMarketingService {

    private final CampagneMarketingRepository campagneMarketingRepository;
    private final CampagneDestinataireRepository campagneDestinataireRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final CompteFideliteRepository compteFideliteRepository;
    private final UserRepository userRepository;
    private final SegmentRepository segmentRepository;
    private final CommunicationTemplateRepository communicationTemplateRepository;
    private final SegmentRuleEvaluator segmentRuleEvaluator;
    private final AuthService authService;
    private final CodeGeneratorService codeGeneratorService;
    private final CampagneEnvoiService campagneEnvoiService;
    private final CampagneMarketingMapper campagneMarketingMapper;
    private final CampagneDestinataireMapper campagneDestinataireMapper;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<CampagneMarketingResponse> search(CampagneStatut statut, CampagneType type, Integer page, Integer size) {
        List<CampagneMarketingResponse> items = campagneMarketingRepository.findAll().stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> type == null || c.getType() == type)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public CampagneMarketingResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public List<CampagneDestinataireResponse> listDestinataires(Long id) {
        getEntity(id);
        return campagneDestinataireRepository.findByCampagneIdOrderByCreatedAtAsc(id).stream()
                .map(campagneDestinataireMapper::toResponse)
                .toList();
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FIDELITE", entityName = "CampagneMarketing")
    public CampagneMarketingResponse create(CampagneMarketingRequest request) {
        requireMarketingOrAdmin();
        validerDates(request.dateDebut(), request.dateFin());

        Segment segment = resolveSegment(request.cible(), request.segmentId());
        CommunicationTemplate template = resolveTemplate(request.templateId());
        validerMessageOuTemplate(request.type(), request.messageContenu(), request.sujetEmail(), template);

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User createdBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        CampagneMarketing campagne = CampagneMarketing.builder()
                .code(codeGeneratorService.generate(CodePrefix.CAMPAGNE))
                .nom(request.nom())
                .description(request.description())
                .type(request.type())
                .statut(CampagneStatut.BROUILLON)
                .cible(request.cible())
                .messageContenu(request.messageContenu())
                .sujetEmail(request.sujetEmail())
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .budgetPrevu(request.budgetPrevu())
                .createdBy(createdBy)
                .segment(segment)
                .template(template)
                .build();

        return toResponse(campagneMarketingRepository.save(campagne));
    }

    @Transactional
    public CampagneMarketingResponse update(Long id, CampagneMarketingRequest request) {
        requireMarketingOrAdmin();
        CampagneMarketing campagne = getEntity(id);
        if (campagne.getStatut() != CampagneStatut.BROUILLON) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une campagne en brouillon peut etre modifiee");
        }
        CampagneMarketingResponse avant = toResponse(campagne);
        validerDates(request.dateDebut(), request.dateFin());

        Segment segment = resolveSegment(request.cible(), request.segmentId());
        CommunicationTemplate template = resolveTemplate(request.templateId());
        validerMessageOuTemplate(request.type(), request.messageContenu(), request.sujetEmail(), template);

        campagne.setNom(request.nom());
        campagne.setDescription(request.description());
        campagne.setType(request.type());
        campagne.setCible(request.cible());
        campagne.setMessageContenu(request.messageContenu());
        campagne.setSujetEmail(request.sujetEmail());
        campagne.setDateDebut(request.dateDebut());
        campagne.setDateFin(request.dateFin());
        campagne.setBudgetPrevu(request.budgetPrevu());
        campagne.setSegment(segment);
        campagne.setTemplate(template);

        CampagneMarketingResponse apres = toResponse(campagneMarketingRepository.save(campagne));
        functionalAuditRecorder.recordUpdate("FIDELITE", "CampagneMarketing", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FIDELITE", entityName = "CampagneMarketing")
    public void delete(Long id) {
        requireMarketingOrAdmin();
        CampagneMarketing campagne = getEntity(id);
        if (campagne.getStatut() != CampagneStatut.BROUILLON) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une campagne en brouillon peut etre supprimee");
        }
        campagneMarketingRepository.delete(campagne);
    }

    @Transactional
    @Auditable(action = ActionAudit.ACTIVATE, module = "FIDELITE", entityName = "CampagneMarketing")
    public CampagneMarketingResponse lancer(Long id, CampagneLancementRequest request) {
        requireMarketingOrAdmin();
        CampagneMarketing campagne = getEntity(id);
        if (campagne.getStatut() != CampagneStatut.BROUILLON && campagne.getStatut() != CampagneStatut.PLANIFIEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne a deja ete lancee ou est terminee/annulee");
        }

        List<Insured> destinataires = resolveDestinataires(campagne, request == null ? null : request.insuredIds());
        List<CanalEnvoi> canaux = resolveCanaux(campagne.getType());

        for (Insured insured : destinataires) {
            for (CanalEnvoi canal : canaux) {
                if (!campagneDestinataireRepository.existsByCampagneIdAndInsuredIdAndCanal(campagne.getId(), insured.getId(), canal)) {
                    campagneDestinataireRepository.save(CampagneDestinataire.builder()
                            .campagne(campagne)
                            .insured(insured)
                            .canal(canal)
                            .statutEnvoi(StatutEnvoiDestinataire.EN_ATTENTE)
                            .build());
                }
            }
        }

        campagne.setStatut(CampagneStatut.EN_COURS);
        campagneMarketingRepository.save(campagne);

        // L'envoi ne doit demarrer qu'APRES le commit de cette transaction : declenche depuis un
        // thread @Async pendant que cette transaction est encore ouverte, il entre en conflit de
        // verrou avec elle sur la meme ligne campagne_marketing (deadlock MySQL constate en test).
        Long campagneId = campagne.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                campagneEnvoiService.envoyerAsync(campagneId);
            }
        });

        return toResponse(campagne);
    }

    @Transactional
    @Auditable(action = ActionAudit.CANCEL, module = "FIDELITE", entityName = "CampagneMarketing")
    public CampagneMarketingResponse annuler(Long id) {
        requireMarketingOrAdmin();
        CampagneMarketing campagne = getEntity(id);
        if (campagne.getStatut() == CampagneStatut.TERMINEE || campagne.getStatut() == CampagneStatut.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne est deja terminee ou annulee");
        }
        campagne.setStatut(CampagneStatut.ANNULEE);
        return toResponse(campagneMarketingRepository.save(campagne));
    }

    private List<Insured> resolveDestinataires(CampagneMarketing campagne, List<Long> insuredIdsPersonnalises) {
        List<Insured> resolved = switch (campagne.getCible()) {
            case TOUS_CLIENTS -> insuredRepository.findAll();
            case CLIENTS_FIDELES -> compteFideliteRepository.findAll().stream()
                    .filter(CompteFidelite::isActive)
                    .map(CompteFidelite::getInsured)
                    .toList();
            case ECHEANCE_PROCHE -> contractsEcheanceProche(30).stream().map(Contract::getInsured).toList();
            case NON_RENOUVELES -> contractsNonRenouveles().stream().map(Contract::getInsured).toList();
            case SEGMENT_PERSONNALISE -> {
                if (insuredIdsPersonnalises == null || insuredIdsPersonnalises.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La liste des destinataires (insuredIds) est obligatoire pour une cible SEGMENT_PERSONNALISE");
                }
                yield insuredRepository.findAllById(insuredIdsPersonnalises);
            }
            case SEGMENT_DYNAMIQUE -> {
                if (campagne.getSegment() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Un segment doit etre associe a la campagne pour une cible SEGMENT_DYNAMIQUE");
                }
                yield segmentRuleEvaluator.resolveMembres(campagne.getSegment());
            }
        };

        Map<Long, Insured> dedup = new LinkedHashMap<>();
        for (Insured insured : resolved) {
            if (insured != null) {
                dedup.put(insured.getId(), insured);
            }
        }
        return List.copyOf(dedup.values());
    }

    /**
     * Reimplemente ici plutot que de reutiliser ContractsService.findEcheanceProche/findNonRenouveles :
     * ces methodes appliquent le scoping salesRep de l'appelant, alors qu'un ciblage de campagne
     * marketing doit porter sur l'ensemble du portefeuille, quel que soit le commercial qui gere
     * chaque contrat.
     */
    private List<Contract> contractsEcheanceProche(int days) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        return contractRepository.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .filter(c -> c.getDateEcheance() != null && !c.getDateEcheance().isBefore(today) && !c.getDateEcheance().isAfter(horizon))
                .toList();
    }

    private List<Contract> contractsNonRenouveles() {
        LocalDate today = LocalDate.now();
        return contractRepository.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .filter(c -> c.getDateEcheance() != null && c.getDateEcheance().isBefore(today))
                .filter(c -> contractRepository.findByContratPrecedentId(c.getId()).isEmpty())
                .toList();
    }

    private List<CanalEnvoi> resolveCanaux(CampagneType type) {
        return switch (type) {
            case SMS -> List.of(CanalEnvoi.SMS);
            case EMAIL -> List.of(CanalEnvoi.EMAIL);
            case SMS_EMAIL -> List.of(CanalEnvoi.SMS, CanalEnvoi.EMAIL);
            case WHATSAPP -> List.of(CanalEnvoi.WHATSAPP);
            case PUSH -> List.of(CanalEnvoi.PUSH);
            case PDF -> List.of(CanalEnvoi.PDF);
        };
    }

    private void validerDates(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFin doit etre posterieure ou egale a dateDebut");
        }
    }

    private Segment resolveSegment(CampagneCible cible, Long segmentId) {
        if (cible == CampagneCible.SEGMENT_DYNAMIQUE && segmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "segmentId est obligatoire pour une cible SEGMENT_DYNAMIQUE");
        }
        if (segmentId == null) {
            return null;
        }
        return segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Segment introuvable: " + segmentId));
    }

    private CommunicationTemplate resolveTemplate(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return communicationTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template introuvable: " + templateId));
    }

    /**
     * Soit messageContenu est renseigne en texte libre, soit un template fournit le contenu — l'un
     * des deux est obligatoire. Idem pour sujetEmail : ignore si un template est fourni (le
     * template a son propre objet), sinon requis pour EMAIL/SMS_EMAIL comme avant.
     */
    private void validerMessageOuTemplate(CampagneType type, String messageContenu, String sujetEmail, CommunicationTemplate template) {
        if (template == null) {
            if (!org.springframework.util.StringUtils.hasText(messageContenu)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageContenu est obligatoire tant qu'aucun templateId n'est fourni");
            }
            if ((type == CampagneType.EMAIL || type == CampagneType.SMS_EMAIL) && !org.springframework.util.StringUtils.hasText(sujetEmail)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sujetEmail est obligatoire pour une campagne EMAIL ou SMS_EMAIL sans template");
            }
        }
    }

    private void requireMarketingOrAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isMarketing(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et au service marketing");
        }
    }

    private CampagneMarketing getEntity(Long id) {
        return campagneMarketingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable: " + id));
    }

    private CampagneMarketingResponse toResponse(CampagneMarketing campagne) {
        long nbDestinataires = campagneDestinataireRepository.findByCampagneIdOrderByCreatedAtAsc(campagne.getId()).size();
        long nbEnvoyes = campagneDestinataireRepository.countByCampagneIdAndStatutEnvoi(campagne.getId(), StatutEnvoiDestinataire.ENVOYE);
        long nbEchecs = campagneDestinataireRepository.countByCampagneIdAndStatutEnvoi(campagne.getId(), StatutEnvoiDestinataire.ECHEC);
        return campagneMarketingMapper.toResponse(campagne, nbDestinataires, nbEnvoyes, nbEchecs);
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
