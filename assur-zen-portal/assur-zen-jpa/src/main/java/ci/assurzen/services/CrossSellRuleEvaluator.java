package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.CrossSellChamp;
import ci.assurzen.entities.CrossSellEvenementType;
import ci.assurzen.entities.CrossSellHistory;
import ci.assurzen.entities.CrossSellOpportunity;
import ci.assurzen.entities.CrossSellRule;
import ci.assurzen.entities.CrossSellRuleCondition;
import ci.assurzen.entities.CrossSellStatut;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.SegmentRuleOperator;
import ci.assurzen.entities.StatutEcheance;
import ci.assurzen.entities.StatutVip;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.CrossSellHistoryRepository;
import ci.assurzen.repositories.CrossSellOpportunityRepository;
import ci.assurzen.repositories.CrossSellRuleConditionRepository;
import ci.assurzen.repositories.CrossSellRuleRepository;
import ci.assurzen.repositories.EcheanceRepository;
import ci.assurzen.repositories.EnqueteSatisfactionRepository;
import ci.assurzen.repositories.ReclamationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Moteur de detection des opportunites de vente croisee. Aucune regle metier n'est codee en dur :
 * la liste des CrossSellRule/CrossSellRuleCondition actives pilote entierement la detection. Deux
 * garde-fous sont en revanche fixes (comportement du moteur, pas des regles configurables) :
 * ne jamais recommander un produit deja detenu, et ne jamais generer un doublon d'opportunite encore
 * active pour le meme (assure, produit).
 */
@Service
@RequiredArgsConstructor
public class CrossSellRuleEvaluator {

    private static final List<CrossSellStatut> STATUTS_NON_BLOQUANTS =
            List.of(CrossSellStatut.REFUSEE, CrossSellStatut.EXPIREE, CrossSellStatut.ANNULEE);

    private final CrossSellRuleRepository crossSellRuleRepository;
    private final CrossSellRuleConditionRepository crossSellRuleConditionRepository;
    private final CrossSellOpportunityRepository crossSellOpportunityRepository;
    private final CrossSellHistoryRepository crossSellHistoryRepository;
    private final ContractRepository contractRepository;
    private final ReclamationRepository reclamationRepository;
    private final EnqueteSatisfactionRepository enqueteSatisfactionRepository;
    private final EcheanceRepository echeanceRepository;
    private final CompteFideliteRepository compteFideliteRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Transactional
    public List<CrossSellOpportunity> genererPour(Insured insured) {
        List<CrossSellRule> regles = crossSellRuleRepository.findByActifTrueOrderByOrdreAsc();
        if (regles.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        List<Contract> contratsSignes = contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .toList();

        List<CrossSellOpportunity> generees = new ArrayList<>();
        for (CrossSellRule regle : regles) {
            if (regle.getDateDebut() != null && today.isBefore(regle.getDateDebut())) {
                continue;
            }
            if (regle.getDateFin() != null && today.isAfter(regle.getDateFin())) {
                continue;
            }
            if (dejaDetient(contratsSignes, regle.getProduitRecommande().getId())) {
                continue;
            }
            if (regle.getProduitDeclencheur() != null && !dejaDetient(contratsSignes, regle.getProduitDeclencheur().getId())) {
                continue;
            }

            List<CrossSellRuleCondition> conditions = crossSellRuleConditionRepository.findByRegleIdOrderByOrdreAsc(regle.getId());
            boolean toutesRemplies = conditions.stream().allMatch(c -> evaluate(c, insured, contratsSignes));
            if (!toutesRemplies) {
                continue;
            }

            boolean dejaOuverte = !crossSellOpportunityRepository
                    .findByInsuredIdAndProduitRecommandeIdAndStatutNotIn(insured.getId(), regle.getProduitRecommande().getId(), STATUTS_NON_BLOQUANTS)
                    .isEmpty();
            if (dejaOuverte) {
                continue;
            }

            generees.add(creerOpportunite(insured, contratsSignes, regle));
        }
        return generees;
    }

    private CrossSellOpportunity creerOpportunite(Insured insured, List<Contract> contratsSignes, CrossSellRule regle) {
        Contract contratReference = contratsSignes.isEmpty() ? null : contratsSignes.get(0);

        CrossSellOpportunity opportunite = CrossSellOpportunity.builder()
                .code(codeGeneratorService.generate(CodePrefix.CROSS_SELL_OPPORTUNITY))
                .insured(insured)
                .contrat(contratReference)
                .produitActuel(contratReference == null ? null : contratReference.getProduct())
                .produitRecommande(regle.getProduitRecommande())
                .score(regle.getPoidsScore())
                .regle(regle)
                .priorite(regle.getPriorite())
                .statut(CrossSellStatut.NOUVELLE)
                .build();
        opportunite = crossSellOpportunityRepository.save(opportunite);

        CrossSellHistory evenement = CrossSellHistory.builder()
                .opportunite(opportunite)
                .typeEvenement(CrossSellEvenementType.CREATION)
                .description("Opportunite generee par la regle " + regle.getNom() + " (score " + regle.getPoidsScore() + ")")
                .build();
        crossSellHistoryRepository.save(evenement);

        functionalAuditRecorder.recordAction("CROSS_SELL", "CrossSellOpportunity", String.valueOf(opportunite.getId()), ActionAudit.CREATE,
                "Opportunite generee automatiquement par la regle " + regle.getNom());

        return opportunite;
    }

    private boolean dejaDetient(List<Contract> contratsSignes, Long produitId) {
        return contratsSignes.stream().anyMatch(c -> c.getProduct() != null && produitId.equals(c.getProduct().getId()));
    }

    private boolean evaluate(CrossSellRuleCondition condition, Insured insured, List<Contract> contratsSignes) {
        Object valeur = resoudreValeur(condition.getChamp(), insured, contratsSignes);
        return applyOperator(condition.getOperateur(), valeur, condition.getValeur());
    }

    private Object resoudreValeur(CrossSellChamp champ, Insured insured, List<Contract> contratsSignes) {
        return switch (champ) {
            case ANCIENNETE_ANNEES -> (double) ancienneteAnnees(contratsSignes);
            case PRIME_ANNUELLE -> primeAnnuelle(contratsSignes);
            case NOMBRE_CONTRATS -> (double) contratsSignes.size();
            case NOMBRE_PRODUITS_DISTINCTS -> (double) contratsSignes.stream()
                    .map(c -> c.getProduct() == null ? null : c.getProduct().getId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            case ECHEANCES_IMPAYEES -> (double) echeancesImpayees(contratsSignes);
            case NPS -> npsMoyen(insured);
            case RECLAMATIONS -> (double) reclamationRepository.findByInsuredId(insured.getId()).size();
            case EST_VIP -> estVip(insured) ? 1.0 : 0.0;
            case TYPE_CLIENT -> insured.getTypeClient() == null ? null : insured.getTypeClient().name();
            case CANAL -> contratsSignes.isEmpty() ? null : (contratsSignes.get(0).getPartenaire() != null ? "COURTIER" : "DIRECT");
        };
    }

    private long ancienneteAnnees(List<Contract> contratsSignes) {
        LocalDate dateReference = contratsSignes.stream()
                .map(Contract::getDateEffet)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        return dateReference == null ? 0 : ChronoUnit.YEARS.between(dateReference, LocalDate.now());
    }

    private double primeAnnuelle(List<Contract> contratsSignes) {
        return contratsSignes.stream().mapToLong(c -> c.getPremium() == null ? 0L : c.getPremium()).sum();
    }

    private long echeancesImpayees(List<Contract> contratsSignes) {
        LocalDate today = LocalDate.now();
        return contratsSignes.stream()
                .flatMap(c -> echeanceRepository.findByContractIdOrderByNumeroEcheanceAsc(c.getId()).stream())
                .filter(e -> e.getStatut() == StatutEcheance.EN_ATTENTE && e.getDateEcheance() != null && e.getDateEcheance().isBefore(today))
                .count();
    }

    private Double npsMoyen(Insured insured) {
        List<EnqueteSatisfaction> repondues = enqueteSatisfactionRepository.findByInsuredId(insured.getId()).stream()
                .filter(e -> e.getStatut() == EnqueteStatut.REPONDUE && e.getScoreNps() != null)
                .toList();
        return repondues.isEmpty() ? null : repondues.stream().mapToInt(EnqueteSatisfaction::getScoreNps).average().orElse(0);
    }

    private boolean estVip(Insured insured) {
        return compteFideliteRepository.findByInsuredId(insured.getId())
                .map(c -> c.getStatutVip() == StatutVip.VIP_ACTIF)
                .orElse(false);
    }

    private boolean applyOperator(SegmentRuleOperator operateur, Object valeur, String valeurAttendue) {
        if (operateur == SegmentRuleOperator.IS_NULL) {
            return valeur == null;
        }
        if (operateur == SegmentRuleOperator.IS_NOT_NULL) {
            return valeur != null;
        }
        if (valeur == null) {
            return false;
        }
        if (valeur instanceof Double reelle) {
            double attendue;
            try {
                attendue = Double.parseDouble(valeurAttendue.trim());
            } catch (NumberFormatException e) {
                return false;
            }
            int cmp = Double.compare(reelle, attendue);
            return switch (operateur) {
                case EQUALS -> cmp == 0;
                case NOT_EQUALS -> cmp != 0;
                case GREATER_THAN -> cmp > 0;
                case GREATER_THAN_OR_EQUAL -> cmp >= 0;
                case LESS_THAN -> cmp < 0;
                case LESS_THAN_OR_EQUAL -> cmp <= 0;
                case CONTAINS, IS_NULL, IS_NOT_NULL, EXPIRE_SOUS_N_JOURS -> false;
            };
        }
        String texte = valeur.toString();
        return switch (operateur) {
            case EQUALS -> texte.equalsIgnoreCase(valeurAttendue.trim());
            case NOT_EQUALS -> !texte.equalsIgnoreCase(valeurAttendue.trim());
            case CONTAINS -> texte.toLowerCase(Locale.ROOT).contains(valeurAttendue.trim().toLowerCase(Locale.ROOT));
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, IS_NULL, IS_NOT_NULL, EXPIRE_SOUS_N_JOURS -> false;
        };
    }
}
