package ci.assurzen.services;

import ci.assurzen.dto.DashboardBackOfficeKpis;
import ci.assurzen.dto.DashboardPeriod;
import ci.assurzen.dto.PartenaireResponse;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.repositories.AvenantRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Alimente GET /dashboard/kpis-back-office, l'API dediee au tableau de bord Angular du back-office.
 * Distincte de DashboardService (API mobile, scopee au commercial connecte) : ici la vue est globale,
 * tous commerciaux/partenaires confondus, deja agregee cote backend (voir docs/api-contracts/dashboard.md
 * cote frontend pour le contrat d'interface).
 */
@Service
@RequiredArgsConstructor
public class DashboardBackOfficeService {

    private final ContractRepository contractRepository;
    private final ExerciceComptableRepository exerciceComptableRepository;
    private final PartenaireService partenaireService;
    private final AvenantRepository avenantRepository;

    @Transactional(readOnly = true)
    public DashboardBackOfficeKpis getKpis(DashboardPeriod period) {
        List<Contract> allContracts = contractRepository.findAll();
        List<Contract> periodContracts = filterByPeriod(allContracts, period);

        DashboardBackOfficeKpis.Volumes volumes = buildVolumes(periodContracts);
        DashboardBackOfficeKpis.Finance finance = buildFinance(periodContracts);
        DashboardBackOfficeKpis.ObjectifGlobal objectifGlobal = buildObjectifGlobal(finance.production());
        DashboardBackOfficeKpis.CycleDeVie cycleDeVie = buildCycleDeVie(allContracts);
        List<DashboardBackOfficeKpis.ProduitRepartition> repartitionParProduit = buildRepartitionParProduit(allContracts);
        DashboardBackOfficeKpis.ObjectifParApporteur objectifParApporteur = buildObjectifParApporteur();

        return new DashboardBackOfficeKpis(volumes, finance, objectifGlobal, cycleDeVie, repartitionParProduit, objectifParApporteur);
    }

    private List<Contract> filterByPeriod(List<Contract> contracts, DashboardPeriod period) {
        DashboardPeriod.Bornes bornes = period.bornes(YearMonth.now(ZoneOffset.UTC));
        if (bornes == null) {
            return contracts;
        }
        return contracts.stream()
                .filter(contract -> bornes.contient(contract.getCreatedAt()))
                .toList();
    }

    private DashboardBackOfficeKpis.Volumes buildVolumes(List<Contract> contracts) {
        long total = contracts.size();
        long signed = contracts.stream().filter(c -> c.getStatus() == ContractStatus.signed).count();
        long pending = contracts.stream().filter(c -> c.getStatus() == ContractStatus.pending).count();
        double conversionRate = total == 0 ? 0d : (double) signed / (double) total;
        return new DashboardBackOfficeKpis.Volumes(total, signed, pending, conversionRate);
    }

    private DashboardBackOfficeKpis.Finance buildFinance(List<Contract> contracts) {
        List<Contract> signedContracts = contracts.stream().filter(c -> c.getStatus() == ContractStatus.signed).toList();

        long production = signedContracts.stream().mapToLong(c -> nz(c.getPremium())).sum();
        long totalCommission = signedContracts.stream().mapToLong(c -> nz(c.getCommissionAmount())).sum();
        long paidCommission = signedContracts.stream()
                .filter(c -> c.getCommissionStatus() == CommissionStatus.paid)
                .mapToLong(c -> nz(c.getCommissionAmount()))
                .sum();
        long partnerShare = signedContracts.stream().mapToLong(c -> nz(c.getPartnerShare())).sum();

        List<Contract> primeNonReversee = signedContracts.stream()
                .filter(c -> c.getPartenaire() != null)
                .filter(c -> c.getPrimeReversementStatus() == StatutReversementPrime.non_reverse)
                .toList();
        long primeNonReverseeCount = primeNonReversee.size();
        long primeNonReverseeMontant = primeNonReversee.stream().mapToLong(c -> nz(c.getPremium())).sum();
        long avenantsCount = avenantRepository.countByContract_IdIn(signedContracts.stream().map(Contract::getId).toList());

        return new DashboardBackOfficeKpis.Finance(production, totalCommission, paidCommission,
                Math.max(0L, totalCommission - paidCommission), partnerShare,
                primeNonReverseeCount, primeNonReverseeMontant, avenantsCount);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private DashboardBackOfficeKpis.ObjectifGlobal buildObjectifGlobal(long production) {
        long montant = exerciceComptableRepository.findByActiveTrue(Sort.by(Sort.Direction.DESC, "dateDebut")).stream()
                .findFirst()
                .map(ExerciceComptable::getObjectifGlobal)
                .filter(Objects::nonNull)
                .map(BigDecimal::longValue)
                .orElse(0L);
        double progression = montant <= 0 ? 0d : (double) production / (double) montant;
        return new DashboardBackOfficeKpis.ObjectifGlobal(montant, progression);
    }

    /**
     * "realises" = contrats signes dont l'echeance est depassee. "nonRenouveles" s'appuie sur le vrai
     * lien de renouvellement Contract.contratPrecedent (pas une heuristique) : un contrat echu est
     * considere renouvele des qu'un autre contrat le reference comme contratPrecedent.
     */
    private DashboardBackOfficeKpis.CycleDeVie buildCycleDeVie(List<Contract> contracts) {
        LocalDate today = LocalDate.now();
        LocalDate dans30Jours = today.plusDays(30);

        List<Contract> realises = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .filter(c -> c.getDateEcheance() != null && c.getDateEcheance().isBefore(today))
                .toList();

        long echeanceProche = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .filter(c -> c.getDateEcheance() != null
                        && !c.getDateEcheance().isBefore(today)
                        && !c.getDateEcheance().isAfter(dans30Jours))
                .count();

        long nonRenouveles = realises.stream()
                .filter(c -> contractRepository.findByContratPrecedentId(c.getId()).isEmpty())
                .count();

        return new DashboardBackOfficeKpis.CycleDeVie(realises.size(), echeanceProche, nonRenouveles);
    }

    private List<DashboardBackOfficeKpis.ProduitRepartition> buildRepartitionParProduit(List<Contract> contracts) {
        Map<Long, DashboardBackOfficeKpis.ProduitRepartition> counts = new LinkedHashMap<>();
        for (Contract contract : contracts) {
            Produit produit = contract.getProduct();
            if (produit == null) {
                continue;
            }
            counts.merge(produit.getId(),
                    new DashboardBackOfficeKpis.ProduitRepartition(produit.getId(), produit.getNomProduit(), 1),
                    (existing, ignored) -> new DashboardBackOfficeKpis.ProduitRepartition(
                            existing.productId(), existing.productName(), existing.count() + 1));
        }
        return counts.values().stream()
                .sorted(Comparator.comparingLong(DashboardBackOfficeKpis.ProduitRepartition::count).reversed())
                .toList();
    }

    /**
     * Reutilise le calcul deja fait par PartenaireService (objectifRealise/tauxRealisation) plutot que
     * de le redupliquer ici. Ne garde que les partenaires avec un objectif fixe (> 0), tries par taux
     * decroissant, conformement au contrat d'interface.
     */
    private DashboardBackOfficeKpis.ObjectifParApporteur buildObjectifParApporteur() {
        List<PartenaireResponse> partenaires = partenaireService.findAllActifs();

        List<DashboardBackOfficeKpis.PartenaireObjectif> avecObjectif = partenaires.stream()
                .filter(p -> p.objectifProduction() != null && p.objectifProduction().signum() > 0)
                .map(p -> new DashboardBackOfficeKpis.PartenaireObjectif(
                        p.id(),
                        (p.prenoms() == null ? "" : p.prenoms() + " ") + p.nom(),
                        p.type() == null ? null : p.type().name(),
                        p.objectifProduction().longValue(),
                        p.objectifRealise() == null ? 0L : p.objectifRealise().longValue(),
                        p.tauxRealisation() == null ? 0d : p.tauxRealisation().doubleValue()))
                .sorted(Comparator.comparingDouble(DashboardBackOfficeKpis.PartenaireObjectif::taux).reversed())
                .toList();

        long totalObjectif = avecObjectif.stream().mapToLong(DashboardBackOfficeKpis.PartenaireObjectif::objectif).sum();
        long totalRealise = avecObjectif.stream().mapToLong(DashboardBackOfficeKpis.PartenaireObjectif::realise).sum();
        double tauxGlobal = totalObjectif <= 0 ? 0d : (double) totalRealise / (double) totalObjectif * 100d;

        return new DashboardBackOfficeKpis.ObjectifParApporteur(totalObjectif, totalRealise, tauxGlobal, avecObjectif);
    }
}
