package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CrossSellRuleConditionRequest;
import ci.assurzen.dto.CrossSellRuleConditionResponse;
import ci.assurzen.dto.CrossSellRuleRequest;
import ci.assurzen.dto.CrossSellRuleResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CrossSellRule;
import ci.assurzen.entities.CrossSellRuleCondition;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.mapper.CrossSellRuleConditionMapper;
import ci.assurzen.mapper.CrossSellRuleMapper;
import ci.assurzen.repositories.CrossSellRuleConditionRepository;
import ci.assurzen.repositories.CrossSellRuleRepository;
import ci.assurzen.repositories.ProduitRepository;
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
public class CrossSellRuleService {

    private final CrossSellRuleRepository crossSellRuleRepository;
    private final CrossSellRuleConditionRepository crossSellRuleConditionRepository;
    private final ProduitRepository produitRepository;
    private final CrossSellRuleMapper crossSellRuleMapper;
    private final CrossSellRuleConditionMapper crossSellRuleConditionMapper;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    /** actif=null : pas de filtre (comportement historique, toutes regles confondues) - meme
     * convention que ProductTypeService.search(). Ajoute suite au retour "DELETE qui repond 204 sans
     * rien supprimer" (regles desactivees restant visibles indefiniment dans la liste, 2026-08-27) :
     * delete() ne fait qu'un soft-delete (actif=false, cf. son javadoc), il n'y avait jusqu'ici aucun
     * moyen de les exclure cote serveur. */
    public List<CrossSellRuleResponse> list(Boolean actif) {
        return crossSellRuleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(regle -> actif == null || regle.isActif() == actif)
                .map(crossSellRuleMapper::toResponse)
                .toList();
    }

    public CrossSellRuleResponse get(Long id) {
        return crossSellRuleMapper.toResponse(getEntity(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "CROSS_SELL", entityName = "CrossSellRule")
    public CrossSellRuleResponse create(CrossSellRuleRequest request) {
        requireCanManage();
        crossSellRuleRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une regle existe deja pour ce code: " + request.code());
        });

        CrossSellRule regle = CrossSellRule.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .produitDeclencheur(request.produitDeclencheurId() != null ? resolveProduit(request.produitDeclencheurId()) : null)
                .produitRecommande(resolveProduit(request.produitRecommandeId()))
                .priorite(request.priorite() != null ? request.priorite() : ReclamationPriorite.NORMALE)
                .poidsScore(request.poidsScore())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .actif(request.actif() == null || request.actif())
                .build();

        return crossSellRuleMapper.toResponse(crossSellRuleRepository.save(regle));
    }

    @Transactional
    public CrossSellRuleResponse update(Long id, CrossSellRuleRequest request) {
        requireCanManage();
        CrossSellRule regle = getEntity(id);
        CrossSellRuleResponse avant = crossSellRuleMapper.toResponse(regle);

        crossSellRuleRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une regle existe deja pour ce code: " + request.code());
                });

        regle.setCode(request.code());
        regle.setNom(request.nom());
        regle.setDescription(request.description());
        regle.setProduitDeclencheur(request.produitDeclencheurId() != null ? resolveProduit(request.produitDeclencheurId()) : null);
        regle.setProduitRecommande(resolveProduit(request.produitRecommandeId()));
        if (request.priorite() != null) {
            regle.setPriorite(request.priorite());
        }
        regle.setPoidsScore(request.poidsScore());
        if (request.ordre() != null) {
            regle.setOrdre(request.ordre());
        }
        regle.setDateDebut(request.dateDebut());
        regle.setDateFin(request.dateFin());
        if (request.actif() != null) {
            regle.setActif(request.actif());
        }

        CrossSellRuleResponse apres = crossSellRuleMapper.toResponse(crossSellRuleRepository.save(regle));
        functionalAuditRecorder.recordUpdate("CROSS_SELL", "CrossSellRule", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "CROSS_SELL", entityName = "CrossSellRule")
    public void delete(Long id) {
        requireCanManage();
        CrossSellRule regle = getEntity(id);
        regle.setActif(false);
        crossSellRuleRepository.save(regle);
    }

    @Transactional
    public CrossSellRuleResponse dupliquer(Long id) {
        requireCanManage();
        CrossSellRule source = getEntity(id);

        String codeCopie = source.getCode() + "-COPIE";
        int suffixe = 2;
        while (crossSellRuleRepository.findByCode(codeCopie).isPresent()) {
            codeCopie = source.getCode() + "-COPIE" + suffixe;
            suffixe++;
        }

        CrossSellRule copie = CrossSellRule.builder()
                .code(codeCopie)
                .nom(source.getNom() + " (copie)")
                .description(source.getDescription())
                .produitDeclencheur(source.getProduitDeclencheur())
                .produitRecommande(source.getProduitRecommande())
                .priorite(source.getPriorite())
                .poidsScore(source.getPoidsScore())
                .ordre(source.getOrdre())
                .dateDebut(source.getDateDebut())
                .dateFin(source.getDateFin())
                .actif(false)
                .build();
        copie = crossSellRuleRepository.save(copie);

        for (CrossSellRuleCondition conditionSource : crossSellRuleConditionRepository.findByRegleIdOrderByOrdreAsc(id)) {
            crossSellRuleConditionRepository.save(CrossSellRuleCondition.builder()
                    .regle(copie)
                    .champ(conditionSource.getChamp())
                    .operateur(conditionSource.getOperateur())
                    .valeur(conditionSource.getValeur())
                    .ordre(conditionSource.getOrdre())
                    .build());
        }

        return crossSellRuleMapper.toResponse(copie);
    }

    public List<CrossSellRuleConditionResponse> listConditions(Long regleId) {
        getEntity(regleId);
        return crossSellRuleConditionRepository.findByRegleIdOrderByOrdreAsc(regleId).stream()
                .map(crossSellRuleConditionMapper::toResponse)
                .toList();
    }

    @Transactional
    public CrossSellRuleConditionResponse ajouterCondition(Long regleId, CrossSellRuleConditionRequest request) {
        requireCanManage();
        CrossSellRule regle = getEntity(regleId);

        CrossSellRuleCondition condition = CrossSellRuleCondition.builder()
                .regle(regle)
                .champ(request.champ())
                .operateur(request.operateur())
                .valeur(request.valeur())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .build();

        return crossSellRuleConditionMapper.toResponse(crossSellRuleConditionRepository.save(condition));
    }

    @Transactional
    public void supprimerCondition(Long regleId, Long conditionId) {
        requireCanManage();
        getEntity(regleId);
        CrossSellRuleCondition condition = crossSellRuleConditionRepository.findByIdAndRegleId(conditionId, regleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condition introuvable: " + conditionId));
        crossSellRuleConditionRepository.delete(condition);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private Produit resolveProduit(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + produitId));
    }

    private CrossSellRule getEntity(Long id) {
        return crossSellRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regle de vente croisee introuvable: " + id));
    }
}
