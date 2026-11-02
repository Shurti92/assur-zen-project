package ci.assurzen.services;

import ci.assurzen.entities.CompteFidelite;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.entities.Segment;
import ci.assurzen.entities.SegmentMembre;
import ci.assurzen.entities.SegmentRule;
import ci.assurzen.entities.SegmentRuleField;
import ci.assurzen.entities.SegmentRuleOperator;
import ci.assurzen.entities.SegmentType;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.SegmentMembreRepository;
import ci.assurzen.repositories.SegmentRuleRepository;
import ci.assurzen.repositories.SinistreRepository;
import lombok.RequiredArgsConstructor;
import static ci.assurzen.utils.SafeAccess.safe;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moteur d'evaluation des segments dynamiques : evalue en memoire (comme le reste du projet, pas
 * de requete SQL generee dynamiquement / pas de Specification JPA).
 *
 * Semantique : les regles d'un meme "groupe" sont combinees en ET, puis les groupes entre eux en
 * OU (forme normale disjonctive). Pour un groupe qui reference au moins un champ "contrat", on
 * cherche s'il existe UN contrat de l'assure qui satisfait simultanement toutes les regles du
 * groupe (semantique "meme contrat", ex. "produit=AUTO AND prime>100000 AND dateExpiration<30j"
 * doit porter sur le meme contrat, pas des contrats differents). Les champs assure/agreges
 * (VILLE_CLIENT, NB_SINISTRES...) sont evalues directement sur l'assure, independamment du contrat.
 */
@Service
@RequiredArgsConstructor
public class SegmentRuleEvaluator {

    private final SegmentRuleRepository segmentRuleRepository;
    private final SegmentMembreRepository segmentMembreRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final SinistreRepository sinistreRepository;
    private final CompteFideliteRepository compteFideliteRepository;

    public List<Insured> resolveMembres(Segment segment) {
        if (segment.getType() == SegmentType.STATIC) {
            return segmentMembreRepository.findBySegmentIdOrderByCreatedAtAsc(segment.getId()).stream()
                    .map(SegmentMembre::getInsured)
                    .toList();
        }

        List<SegmentRule> rules = segmentRuleRepository.findBySegmentIdOrderByGroupeAscOrdreAsc(segment.getId());
        if (rules.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<SegmentRule>> groupes = rules.stream()
                .collect(Collectors.groupingBy(SegmentRule::getGroupe, LinkedHashMap::new, Collectors.toList()));

        return insuredRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(insured -> matches(insured, groupes))
                .toList();
    }

    private boolean matches(Insured insured, Map<Integer, List<SegmentRule>> groupes) {
        List<Contract> contrats = contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId());
        for (List<SegmentRule> groupeRules : groupes.values()) {
            if (matchesGroupe(insured, contrats, groupeRules)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGroupe(Insured insured, List<Contract> contrats, List<SegmentRule> groupeRules) {
        boolean besoinContrat = groupeRules.stream().anyMatch(r -> isContractField(r.getChamp()));
        if (!besoinContrat) {
            return groupeRules.stream().allMatch(r -> evaluate(r, insured, null));
        }
        if (contrats.isEmpty()) {
            return false;
        }
        for (Contract contract : contrats) {
            if (groupeRules.stream().allMatch(r -> evaluate(r, insured, contract))) {
                return true;
            }
        }
        return false;
    }

    private boolean isContractField(SegmentRuleField champ) {
        return switch (champ) {
            case PRODUIT, COMPAGNIE, PRIME, DATE_ECHEANCE, DATE_EFFET, STATUT_CONTRAT, MODE_PAIEMENT -> true;
            case VILLE_CLIENT, TYPE_CLIENT, NB_SINISTRES, NB_CONTRATS, PALIER_FIDELITE -> false;
        };
    }

    private boolean evaluate(SegmentRule rule, Insured insured, Contract contract) {
        Object value = resolveValue(rule.getChamp(), insured, contract);
        return applyOperator(rule.getOperateur(), value, rule.getValeur());
    }

    private Object resolveValue(SegmentRuleField champ, Insured insured, Contract contract) {
        return switch (champ) {
            case PRODUIT -> (contract == null || contract.getProduct() == null) ? null : safe(contract.getProduct()::getNomProduit);
            case COMPAGNIE -> (contract == null || contract.getCompagnie() == null) ? null : safe(contract.getCompagnie()::getNom);
            case PRIME -> contract == null ? null : contract.getPremium();
            case DATE_ECHEANCE -> contract == null ? null : contract.getDateEcheance();
            case DATE_EFFET -> contract == null ? null : contract.getDateEffet();
            case STATUT_CONTRAT -> (contract == null || contract.getStatus() == null) ? null : contract.getStatus().name();
            case MODE_PAIEMENT -> (contract == null || contract.getModePaiement() == null) ? null : contract.getModePaiement().name();
            case VILLE_CLIENT -> insured.getClientCity();
            case TYPE_CLIENT -> insured.getTypeClient() == null ? null : insured.getTypeClient().name();
            case NB_SINISTRES -> (long) sinistreRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).size();
            case NB_CONTRATS -> (long) contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).size();
            case PALIER_FIDELITE -> compteFideliteRepository.findByInsuredId(insured.getId())
                    .map(CompteFidelite::getPalier)
                    .map(PalierFidelite::getCode)
                    .orElse(null);
        };
    }

    private boolean applyOperator(SegmentRuleOperator operateur, Object value, String valeur) {
        if (operateur == SegmentRuleOperator.IS_NULL) {
            return value == null;
        }
        if (operateur == SegmentRuleOperator.IS_NOT_NULL) {
            return value != null;
        }
        if (operateur == SegmentRuleOperator.EXPIRE_SOUS_N_JOURS) {
            if (!(value instanceof LocalDate date) || valeur == null) {
                return false;
            }
            LocalDate today = LocalDate.now();
            LocalDate horizon = today.plusDays(Integer.parseInt(valeur.trim()));
            return !date.isBefore(today) && !date.isAfter(horizon);
        }
        if (value == null) {
            return false;
        }

        return switch (operateur) {
            case EQUALS -> compareEquals(value, valeur);
            case NOT_EQUALS -> !compareEquals(value, valeur);
            case CONTAINS -> value.toString().toLowerCase(Locale.ROOT).contains(valeur.toLowerCase(Locale.ROOT));
            case GREATER_THAN -> compareOrdered(value, valeur) > 0;
            case GREATER_THAN_OR_EQUAL -> compareOrdered(value, valeur) >= 0;
            case LESS_THAN -> compareOrdered(value, valeur) < 0;
            case LESS_THAN_OR_EQUAL -> compareOrdered(value, valeur) <= 0;
            case IS_NULL, IS_NOT_NULL, EXPIRE_SOUS_N_JOURS -> false;
        };
    }

    private boolean compareEquals(Object value, String valeur) {
        if (value instanceof LocalDate date) {
            return date.equals(LocalDate.parse(valeur.trim()));
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).compareTo(new BigDecimal(valeur.trim())) == 0;
        }
        return value.toString().equalsIgnoreCase(valeur.trim());
    }

    private int compareOrdered(Object value, String valeur) {
        if (value instanceof LocalDate date) {
            return date.compareTo(LocalDate.parse(valeur.trim()));
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).compareTo(new BigDecimal(valeur.trim()));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operateur numerique/date applique a un champ texte");
    }
}
