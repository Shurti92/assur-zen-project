package ci.assurzen.services;

import ci.assurzen.dto.DashboardKPIs;
import ci.assurzen.dto.DashboardPeriod;
import ci.assurzen.dto.PartenaireResponse;
import ci.assurzen.dto.ProductionHistoryEntry;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.ProductType;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.repositories.AvenantRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ContractRepository contractRepository;
    private final ProfileRepository profileRepository;
    private final PartenaireRepository partenaireRepository;
    private final AvenantRepository avenantRepository;
    private final AuthService authService;
    private final PartenaireService partenaireService;
    private final CommercialRepository commercialRepository;
    private final MoisComptableRepository moisComptableRepository;
    private final CommercialPerformanceService commercialPerformanceService;

    static final int HISTORY_MONTHS_MAX = 24;

    /** Filet de repli UNIQUEMENT pour un commercial interne connecte (pas de notion d'objectif
     * individuel pour ce profil dans le modele actuel) - jamais configure nulle part en pratique,
     * reste a 0 sauf ajout futur d'un objectif par commercial. Pour un partenaire connecte, l'objectif
     * vient desormais de Partenaire.objectifProduction (cf. getKpis) - decision produit du
     * 2026-08-24, la "barre d'objectif" du dashboard partenaire etait vide en RECETTE car cette
     * propriete n'a jamais ete configuree dans aucun environnement. */
    @Value("${app.monthly-objective:0}")
    private long monthlyObjective;

    @Transactional(readOnly = true)
    public DashboardKPIs getKpis(DashboardPeriod period) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        Long partenaireId = resolvePartenaireId(principal);
        List<Contract> allContracts = resolveContracts(principal, partenaireId);
        DashboardPeriod.Bornes bornes = period.bornes(YearMonth.now(ZoneOffset.UTC));
        // Deux decoupages (2026-10-04, base unique de la production avec production-history) :
        // - contrats CREES dans la periode (createdAt) : totalContracts, pendingContracts, conversionRate, avenants ;
        // - contrats SIGNES dans la periode (dateSignature) : production, signedContracts, commissions,
        //   partnerShare et primes non reversees, ainsi que la repartition par branche.
        List<Contract> crees = bornes == null ? allContracts
                : allContracts.stream().filter(c -> bornes.contient(c.getCreatedAt())).toList();
        List<Contract> signes = bornes == null ? signed(allContracts).toList()
                : signed(allContracts).filter(c -> bornes.contient(dateSignature(c))).toList();
        List<Contract> creesAvant = bornes == null ? null
                : allContracts.stream().filter(c -> bornes.precedente().contient(c.getCreatedAt())).toList();
        List<Contract> signesAvant = bornes == null ? null
                : signed(allContracts).filter(c -> bornes.precedente().contient(dateSignature(c))).toList();

        long totalContracts = crees.size();
        long signedContracts = signes.size();
        long pendingContracts = crees.stream().filter(c -> c.getStatus() == ContractStatus.pending).count();
        long production = signes.stream()
                .mapToLong(c -> nz(c.getPremium()))
                .sum();
        long totalCommission = signes.stream()
                .mapToLong(c -> nz(c.getCommissionAmount()))
                .sum();
        long paidCommission = signes.stream()
                .filter(c -> c.getCommissionStatus() == CommissionStatus.paid)
                .mapToLong(c -> nz(c.getCommissionAmount()))
                .sum();
        long partnerShare = signes.stream()
                .mapToLong(c -> nz(c.getPartnerShare()))
                .sum();
        long pendingPartnerShare = signes.stream()
                .filter(c -> c.getCommissionStatus() == CommissionStatus.pending)
                .mapToLong(c -> nz(c.getPartnerShare()))
                .sum();
        long paidPartnerShare = signes.stream()
                .filter(c -> c.getCommissionStatus() == CommissionStatus.paid)
                .mapToLong(c -> nz(c.getPartnerShare()))
                .sum();
        List<Contract> primeNonReversee = signes.stream()
                .filter(c -> c.getPartenaire() != null)
                .filter(c -> c.getPrimeReversementStatus() == StatutReversementPrime.non_reverse)
                .toList();
        long primeNonReverseeCount = primeNonReversee.size();
        long primeNonReverseeMontant = primeNonReversee.stream().mapToLong(c -> nz(c.getPremium())).sum();
        long avenantsCount = avenantRepository.countByContract_IdIn(crees.stream().map(Contract::getId).toList());

        // Taux de transformation sur la cohorte des contrats crees dans la periode (jamais > 100 %).
        long creesSignes = signed(crees).count();
        double conversionRate = totalContracts == 0 ? 0d : (double) creesSignes / (double) totalContracts;

        // Objectif : celui du partenaire connecte (Partenaire.objectifProduction, deja fiabilise et
        // exercice-aware via PartenaireService) s'il en a un ; a defaut (commercial interne, ou
        // partenaire sans objectif fixe), repli sur app.monthly-objective (jamais configure a ce jour).
        long objectif = monthlyObjective;
        double objectiveProgress;
        if (partenaireId != null) {
            PartenaireResponse partenaire = partenaireService.findById(partenaireId);
            objectif = partenaire.objectifProduction() == null ? 0L : partenaire.objectifProduction().longValue();
            objectiveProgress = partenaire.tauxRealisation() == null ? 0d
                    : Math.min(partenaire.tauxRealisation().doubleValue() / 100d, 1d);
        } else {
            objectiveProgress = monthlyObjective <= 0 ? 0d : Math.min((double) production / (double) monthlyObjective, 1d);
        }

        return new DashboardKPIs(
                totalContracts,
                signedContracts,
                pendingContracts,
                production,
                totalCommission,
                paidCommission,
                Math.max(0L, totalCommission - paidCommission),
                partnerShare,
                pendingPartnerShare,
                round(conversionRate),
                objectif,
                round(objectiveProgress),
                paidPartnerShare,
                primeNonReverseeCount,
                primeNonReverseeMontant,
                avenantsCount,
                creesAvant == null ? null : (long) creesAvant.size(),
                signesAvant == null ? null : (long) signesAvant.size(),
                signesAvant == null ? null : signesAvant.stream().mapToLong(c -> nz(c.getPremium())).sum(),
                signesAvant == null ? null : signesAvant.stream().mapToLong(c -> nz(c.getPartnerShare())).sum(),
                repartitionParBranche(signes, production)
        );
    }

    /**
     * Date de signature retenue pour decouper la production par periode : signedAt, ou a defaut la date
     * de creation (UTC). signedAt n'est renseigne que s'il est fourni a la creation du contrat : sur la
     * copie de la prod du 2026-10-04, 360 contrats signes sur 449 ne l'ont pas (surtout les ventes
     * directes) - sans ce repli, ils disparaitraient de toute periode.
     */
    static LocalDate dateSignature(Contract contract) {
        if (contract.getSignedAt() != null) {
            return contract.getSignedAt();
        }
        return contract.getCreatedAt() == null ? null : contract.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static java.util.stream.Stream<Contract> signed(List<Contract> contracts) {
        return contracts.stream().filter(c -> c.getStatus() == ContractStatus.signed);
    }

    private List<DashboardKPIs.BrancheRepartition> repartitionParBranche(List<Contract> contracts, long production) {
        Map<Long, List<Contract>> parBranche = new LinkedHashMap<>();
        Map<Long, ProductType> branches = new LinkedHashMap<>();
        signed(contracts).forEach(c -> {
            ProductType type = c.getProduct() == null ? null : c.getProduct().getProductType();
            Long cle = type == null ? null : type.getId();
            parBranche.computeIfAbsent(cle, k -> new ArrayList<>()).add(c);
            if (type != null) {
                branches.putIfAbsent(cle, type);
            }
        });
        return parBranche.entrySet().stream()
                .map(e -> {
                    ProductType type = branches.get(e.getKey());
                    long prod = e.getValue().stream().mapToLong(c -> nz(c.getPremium())).sum();
                    return new DashboardKPIs.BrancheRepartition(
                            e.getKey(),
                            type == null ? null : type.getCode(),
                            type == null ? "Sans branche" : type.getNom(),
                            type == null ? null : type.getCouleur(),
                            e.getValue().size(),
                            prod,
                            production == 0 ? 0d : round((double) prod / (double) production));
                })
                .sorted(Comparator.comparingLong(DashboardKPIs.BrancheRepartition::production).reversed())
                .toList();
    }

    /**
     * Historique mensuel de production de l'utilisateur connecte (partenaire : ses contrats ;
     * commercial interne : sa performance mensuelle, snapshot fige si le mois est valide/paye ; autre
     * compte interne : ses contrats en tant que salesRep, sans objectif). Les `months` derniers mois
     * comptables deja commences, du plus ancien au plus recent (2026-10-03, refonte Espace Partenaire).
     */
    @Transactional(readOnly = true)
    public List<ProductionHistoryEntry> getProductionHistory(int months) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        int nb = Math.max(1, Math.min(months, HISTORY_MONTHS_MAX));
        LocalDate aujourdhui = LocalDate.now();
        List<MoisComptable> moisList = new ArrayList<>(moisComptableRepository
                .findByDateDebutLessThanEqualOrderByDateDebutDesc(aujourdhui).stream().limit(nb).toList());
        moisList.sort(Comparator.comparing(MoisComptable::getDateDebut));

        Long partenaireId = resolvePartenaireId(principal);
        Partenaire partenaire = partenaireId == null ? null : partenaireRepository.findById(partenaireId).orElse(null);
        Commercial commercial = partenaireId != null ? null
                : commercialRepository.findByProfile_UserId(principal.userId()).orElse(null);
        List<Contract> signes = partenaireId != null
                ? contractRepository.findByStatusAndPartenaireId(ContractStatus.signed, partenaireId)
                : contractRepository.findByStatusAndSalesRepId(ContractStatus.signed, principal.userId());

        return moisList.stream().map(mois -> {
            List<Contract> duMois = signes.stream().filter(c -> signeDans(c, mois)).toList();
            long production = duMois.stream().mapToLong(c -> nz(c.getPremium())).sum();
            Long objectif = null;
            String source = null;
            if (commercial != null) {
                CommercialPerformance perf = commercialPerformanceService.resoudrePourLecture(commercial, mois);
                if (perf.getRealizedAmount() != null) {
                    production = perf.getRealizedAmount().setScale(0, RoundingMode.HALF_UP).longValue();
                }
                if (perf.getObjectiveAmount() != null && perf.getObjectiveAmount().signum() > 0) {
                    objectif = perf.getObjectiveAmount().setScale(0, RoundingMode.HALF_UP).longValue();
                    source = "MENSUEL";
                }
            } else if (partenaire != null) {
                objectif = objectifMensuelProratise(partenaire, mois);
                source = objectif == null ? null : "ANNUEL_PRORATISE";
            }
            boolean enCours = !aujourdhui.isBefore(mois.getDateDebut()) && !aujourdhui.isAfter(mois.getDateFin());
            return new ProductionHistoryEntry(mois.getId(), YearMonth.from(mois.getDateDebut()).toString(),
                    mois.getLibelle(), mois.getDateDebut(), mois.getDateFin(),
                    production, duMois.size(), objectif, source, enCours);
        }).toList();
    }

    private static boolean signeDans(Contract contract, MoisComptable mois) {
        LocalDate signature = dateSignature(contract);
        return signature != null && !signature.isBefore(mois.getDateDebut()) && !signature.isAfter(mois.getDateFin());
    }

    /** Objectif annuel du partenaire (Partenaire.objectifProduction, par exercice) divise par le nombre
     * de mois de son exercice - uniquement pour les mois de cet exercice ; null sinon ou sans objectif. */
    private Long objectifMensuelProratise(Partenaire partenaire, MoisComptable mois) {
        if (partenaire.getObjectifProduction() == null || partenaire.getObjectifProduction().signum() <= 0
                || partenaire.getExerciceComptable() == null || mois.getExerciceComptable() == null
                || !partenaire.getExerciceComptable().getId().equals(mois.getExerciceComptable().getId())) {
            return null;
        }
        long nbMois = moisComptableRepository.countByExerciceComptableId(partenaire.getExerciceComptable().getId());
        if (nbMois <= 0) {
            return null;
        }
        return partenaire.getObjectifProduction().divide(BigDecimal.valueOf(nbMois), 0, RoundingMode.HALF_UP).longValue();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * Si le compte connecte EST un partenaire (son Profile est rattache a un Partenaire, meme
     * mecanisme que AuthService.buildUserResponse/ContractsService.estPartenaireConnecte), ses KPIs
     * portent sur TOUS ses contrats en tant que partenaire, peu importe quel commercial interne
     * (salesRep) les a traites - Partenaire et salesRep sont deux notions independantes. Sinon
     * (commercial/admin interne), comportement inchange : ses propres contrats en tant que salesRep.
     * Bug reel constate le 2026-08-21 : l'app partenaire appelait deja cet endpoint en s'attendant a
     * ses propres KPIs, mais recevait ceux du commercial qui a traite ses contrats (souvent zero ou un
     * montant sans rapport, un partenaire n'etant quasiment jamais lui-meme le salesRep enregistre).
     */
    private List<Contract> resolveContracts(AuthenticatedUserPrincipal principal, Long partenaireId) {
        return partenaireId != null
                ? contractRepository.findByPartenaireIdOrderByCreatedAtDesc(partenaireId)
                : contractRepository.findBySalesRepIdOrderByCreatedAtDesc(principal.userId());
    }

    private Long resolvePartenaireId(AuthenticatedUserPrincipal principal) {
        return profileRepository.findByUserId(principal.userId())
                .map(profile -> partenaireRepository.findByProfile_Id(profile.getId()))
                .filter(partenaires -> !partenaires.isEmpty())
                .map(partenaires -> partenaires.get(0).getId())
                .orElse(null);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
