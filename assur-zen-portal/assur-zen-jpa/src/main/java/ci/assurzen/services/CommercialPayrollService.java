package ci.assurzen.services;

import ci.assurzen.dto.CommercialBenefitSummary;
import ci.assurzen.dto.CommercialPayrollEntry;
import ci.assurzen.entities.CommercialBenefitPayment;
import ci.assurzen.entities.TypeAvantageCommercial;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.entities.StatutCommercialPerformance;
import ci.assurzen.repositories.CommercialPerformanceRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepare les elements necessaires a la paie des commerciaux (§45, contrat "Module Commercial",
 * 2026-09-07). Service separe de CommercialPerformanceService (§74 : "ne pas creer un enorme
 * CommercialService, separer Commercial/Objective/Production/Performance/Bonus/Payroll") - aucun nouveau
 * calcul financier ici : les valeurs viennent de CommercialPerformanceService.
 *
 * IMPORTANT (§45) : ce service NE PRETEND PAS effectuer une paie complete - il n'existe aucun module
 * de paie dans ce projet. Il fournit uniquement les composantes de remuneration (prime de base,
 * avantages, bonus) - pas de charges sociales, pas d'impots, pas de net a payer.
 *
 * Correctif 2026-10-02 : la version precedente ne renvoyait QUE les performances persistees et deja
 * definitives (VALIDATED/PAID/CLOSED) - le mois en cours n'y figurait donc jamais et l'onglet Paie
 * restait vide. Desormais tous les commerciaux ACTIFS (et ceux ayant deja une ligne ce mois-la) sont
 * listes, avec les valeurs figees si la periode est verrouillee, recalculees en direct sinon (lecture
 * pure, jamais persistee). `estime` distingue un bonus encore estime (§77 : bonus_estimated vs
 * bonus_validated/paid) d'une donnee definitive transmissible a la paie.
 */
@Service
@RequiredArgsConstructor
public class CommercialPayrollService {

    private final CommercialPerformanceRepository repository;
    private final CommercialService commercialService;
    private final CommercialRepository commercialRepository;
    private final MoisComptableRepository moisComptableRepository;
    private final CommercialPerformanceService performanceService;
    private final CommercialBenefitPaymentService benefitPaymentService;

    @Transactional(readOnly = true)
    public List<CommercialPayrollEntry> forMois(Long moisComptableId) {
        commercialService.requireAdmin();
        MoisComptable mois = moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));

        Map<Long, Commercial> commerciaux = new LinkedHashMap<>();
        repository.findByMoisComptableId(moisComptableId)
                .forEach(perf -> commerciaux.putIfAbsent(perf.getCommercial().getId(), perf.getCommercial()));
        commercialRepository.findByStatut(StatutCommercial.ACTIVE)
                .forEach(commercial -> commerciaux.putIfAbsent(commercial.getId(), commercial));

        List<CommercialPerformance> performances = commerciaux.values().stream()
                .map(commercial -> performanceService.resoudrePourLecture(commercial, mois))
                .sorted(Comparator.comparing((CommercialPerformance p) -> nomComplet(p.getCommercial()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, SycaPayTransaction> transactions = performanceService.transactionsParPerformance(performances);

        Map<Long, Map<TypeAvantageCommercial, CommercialBenefitPayment>> avantages = benefitPaymentService.paiementsDuMois(moisComptableId);
        Map<Long, SycaPayTransaction> transactionsAvantages = benefitPaymentService.transactionsParPaiement(
                avantages.values().stream().flatMap(m -> m.values().stream()).toList());

        return performances.stream()
                .map(perf -> toEntry(perf, perf.getId() == null ? null : transactions.get(perf.getId()),
                        avantages.getOrDefault(perf.getCommercial().getId(), Map.of()), transactionsAvantages))
                .toList();
    }

    /** Agregats des avantages (transport / communication) a payer et payes pour un mois. */
    @Transactional(readOnly = true)
    public CommercialBenefitSummary resumeAvantages(Long moisComptableId) {
        return benefitPaymentService.resume(moisComptableId, forMois(moisComptableId));
    }

    private boolean estDefinitif(StatutCommercialPerformance statut) {
        return statut == StatutCommercialPerformance.VALIDATED
                || statut == StatutCommercialPerformance.PAID
                || statut == StatutCommercialPerformance.CLOSED;
    }

    private String nomComplet(Commercial commercial) {
        Profile profile = commercial.getProfile();
        return profile.getFirstname() + " " + profile.getLastname();
    }

    private CommercialPayrollEntry toEntry(CommercialPerformance p, SycaPayTransaction transaction,
                                           Map<TypeAvantageCommercial, CommercialBenefitPayment> avantages,
                                           Map<Long, SycaPayTransaction> transactionsAvantages) {
        Commercial commercial = p.getCommercial();
        BigDecimal totalVariable = p.getBaseSalarySnapshot()
                .add(p.getTransportBenefitSnapshot())
                .add(p.getPhoneAllowanceSnapshot())
                .add(p.getBonusAmount());

        return new CommercialPayrollEntry(
                commercial.getId(),
                commercial.getEmployeeCode(),
                nomComplet(commercial),
                p.getMoisComptable().getId(),
                p.getMoisComptable().getLibelle(),
                p.getBaseSalarySnapshot(),
                p.getTransportBenefitSnapshot(),
                p.getPhoneAllowanceSnapshot(),
                p.getBonusAmount(),
                totalVariable,
                p.getStatut(),
                !estDefinitif(p.getStatut()),
                p.getObjectiveAmount(),
                p.getRealizedAmount(),
                p.getAchievementRate(),
                p.isEligible(),
                performanceService.estPayable(p, transaction),
                transaction == null ? null : transaction.getStatus().name(),
                p.getBonusRateSnapshot(),
                performanceService.seuilEligibilite(),
                commercial.getProfile().getVillePartenaire() == null ? null : commercial.getProfile().getVillePartenaire().getNomVille(),
                masquerTelephone(commercial.getProfile().getPhone()),
                p.getPaidAt(),
                p.getModePaiement(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getTransactionReference(),
                blocAvantage(TypeAvantageCommercial.TRANSPORT, p, avantages, transactionsAvantages),
                blocAvantage(TypeAvantageCommercial.COMMUNICATION, p, avantages, transactionsAvantages)
        );
    }

    private ci.assurzen.dto.CommercialBenefitStatus blocAvantage(TypeAvantageCommercial type, CommercialPerformance p,
                                                                 Map<TypeAvantageCommercial, CommercialBenefitPayment> avantages,
                                                                 Map<Long, SycaPayTransaction> transactionsAvantages) {
        CommercialBenefitPayment paiement = avantages.get(type);
        return benefitPaymentService.statut(type, CommercialBenefitPaymentService.montantFiche(p, type), paiement,
                paiement == null ? null : transactionsAvantages.get(paiement.getId()));
    }

    /** "0700000012" -&gt; "07 •• •• 12" : assez pour reconnaitre le numero, jamais le numero complet. */
    static String masquerTelephone(String telephone) {
        if (telephone == null) {
            return null;
        }
        String chiffres = telephone.replaceAll("\\D", "");
        if (chiffres.length() < 4) {
            return null;
        }
        return chiffres.substring(0, 2) + " •• •• " + chiffres.substring(chiffres.length() - 2);
    }
}
