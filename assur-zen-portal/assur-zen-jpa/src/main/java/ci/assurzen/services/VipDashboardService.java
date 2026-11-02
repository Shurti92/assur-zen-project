package ci.assurzen.services;

import ci.assurzen.dto.VipMensuelItem;
import ci.assurzen.dto.VipOverviewResponse;
import ci.assurzen.dto.VipParDimensionItem;
import ci.assurzen.dto.VipParNiveauItem;
import ci.assurzen.dto.VipTopClientItem;
import ci.assurzen.entities.CompteFidelite;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.EchangeStatut;
import ci.assurzen.entities.FidelisationHistorique;
import ci.assurzen.entities.MouvementFideliteType;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.RecompenseEchange;
import ci.assurzen.entities.StatutVip;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.FidelisationHistoriqueRepository;
import ci.assurzen.repositories.PalierFideliteRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.RecompenseEchangeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import static ci.assurzen.utils.SafeAccess.safe;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard analytique lecture-seule des Clients VIP : agrege CompteFidelite/PalierFidelite/
 * FidelisationHistorique/RecompenseEchange/Contract. Pas de CRUD ici, uniquement des GET. Reserve
 * a ADMIN/MARKETING/MANAGER, meme convention que SatisfactionDashboardService.
 */
@Service
@RequiredArgsConstructor
public class VipDashboardService {

    private final CompteFideliteRepository compteFideliteRepository;
    private final PalierFideliteRepository palierFideliteRepository;
    private final FidelisationHistoriqueRepository fidelisationHistoriqueRepository;
    private final RecompenseEchangeRepository recompenseEchangeRepository;
    private final ContractRepository contractRepository;
    private final ProfileRepository profileRepository;
    private final AuthService authService;

    public VipOverviewResponse overview() {
        requireAccess();
        List<CompteFidelite> comptes = compteFideliteRepository.findAll();
        List<CompteFidelite> vip = comptesVip(comptes);

        Double scoreMoyen = vip.stream()
                .map(CompteFidelite::getScoreFidelite)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average().stream().boxed().findFirst().orElse(null);

        long primeGeneree = vip.stream().mapToLong(c -> primeSignee(c)).sum();

        List<RecompenseEchange> echanges = recompenseEchangeRepository.findAll().stream()
                .filter(e -> e.getStatut() == EchangeStatut.VALIDE || e.getStatut() == EchangeStatut.LIVREE)
                .toList();
        long pointsDistribues = echanges.stream().mapToLong(RecompenseEchange::getPointsUtilises).sum();

        YearMonth moisCourant = YearMonth.now();
        List<FidelisationHistorique> historique = fidelisationHistoriqueRepository.findAll();
        long entrees = historique.stream()
                .filter(h -> h.getTypeMouvement() == MouvementFideliteType.ENTREE_VIP)
                .filter(h -> YearMonth.from(h.getCreatedAt().atZone(ZoneOffset.UTC)).equals(moisCourant))
                .count();
        long sorties = historique.stream()
                .filter(h -> h.getTypeMouvement() == MouvementFideliteType.SORTIE_VIP)
                .filter(h -> YearMonth.from(h.getCreatedAt().atZone(ZoneOffset.UTC)).equals(moisCourant))
                .count();

        return new VipOverviewResponse(vip.size(), comptes.size(), scoreMoyen, primeGeneree,
                echanges.size(), pointsDistribues, entrees, sorties);
    }

    public List<VipParNiveauItem> parNiveau() {
        requireAccess();
        List<CompteFidelite> comptes = compteFideliteRepository.findAll();
        Map<Long, Long> nombreParPalier = comptes.stream()
                .filter(c -> c.getPalier() != null)
                .collect(Collectors.groupingBy(c -> c.getPalier().getId(), Collectors.counting()));

        return palierFideliteRepository.findAll().stream()
                .sorted(Comparator.comparing(PalierFidelite::getSeuilPoints))
                .map(p -> new VipParNiveauItem(p.getId(), p.getLibelle(), p.getCouleur(), p.getIcone(), p.isEstVip(),
                        nombreParPalier.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    /** Tendance sur les 12 derniers mois calendaires (entrees/sorties VIP, mois vides inclus). */
    public List<VipMensuelItem> mensuel() {
        requireAccess();
        List<FidelisationHistorique> historique = fidelisationHistoriqueRepository.findAll().stream()
                .filter(h -> h.getTypeMouvement() == MouvementFideliteType.ENTREE_VIP || h.getTypeMouvement() == MouvementFideliteType.SORTIE_VIP)
                .toList();

        Map<YearMonth, List<FidelisationHistorique>> parMois = historique.stream()
                .collect(Collectors.groupingBy(h -> YearMonth.from(h.getCreatedAt().atZone(ZoneOffset.UTC))));

        YearMonth moisCourant = YearMonth.now();
        List<VipMensuelItem> resultat = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mois = moisCourant.minusMonths(i);
            List<FidelisationHistorique> duMois = parMois.getOrDefault(mois, List.of());
            long entrees = duMois.stream().filter(h -> h.getTypeMouvement() == MouvementFideliteType.ENTREE_VIP).count();
            long sorties = duMois.stream().filter(h -> h.getTypeMouvement() == MouvementFideliteType.SORTIE_VIP).count();
            resultat.add(new VipMensuelItem(mois.toString(), entrees, sorties));
        }
        return resultat;
    }

    public List<VipTopClientItem> topClients(int limite) {
        requireAccess();
        return comptesVip(compteFideliteRepository.findAll()).stream()
                .filter(c -> c.getScoreFidelite() != null)
                .sorted(Comparator.comparing(CompteFidelite::getScoreFidelite).reversed())
                .limit(limite)
                .map(c -> new VipTopClientItem(
                        c.getInsured() == null ? null : safe(c.getInsured()::getId),
                        insuredName(c),
                        c.getScoreFidelite(),
                        c.getPalier() == null ? null : c.getPalier().getLibelle(),
                        c.getSoldePoints()))
                .toList();
    }

    public List<VipParDimensionItem> parProduit() {
        requireAccess();
        return parDimension(c -> c.getProduct(), Produit::getId, Produit::getNomProduit);
    }

    public List<VipParDimensionItem> parGestionnaire() {
        requireAccess();
        return parDimension(Contract::getSalesRep, User::getId, this::displayName);
    }

    public List<VipParDimensionItem> parCourtier() {
        requireAccess();
        return parDimension(Contract::getPartenaire, Partenaire::getId, this::displayNamePartenaire);
    }

    private <T> List<VipParDimensionItem> parDimension(Function<Contract, T> extracteur, Function<T, Long> idExtractor, Function<T, String> nomExtractor) {
        List<Long> insuredIdsVip = comptesVip(compteFideliteRepository.findAll()).stream()
                .map(c -> c.getInsured() == null ? null : c.getInsured().getId())
                .filter(java.util.Objects::nonNull)
                .toList();

        List<Contract> contratsVip = contractRepository.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .filter(c -> c.getInsured() != null && insuredIdsVip.contains(c.getInsured().getId()))
                .toList();

        Map<T, List<Contract>> parGroupe = contratsVip.stream()
                .filter(c -> extracteur.apply(c) != null)
                .collect(Collectors.groupingBy(extracteur));

        return parGroupe.entrySet().stream()
                .map(entry -> new VipParDimensionItem(
                        idExtractor.apply(entry.getKey()),
                        nomExtractor.apply(entry.getKey()),
                        entry.getValue().stream().map(Contract::getInsured).map(i -> i == null ? null : i.getId()).distinct().count(),
                        entry.getValue().stream().mapToLong(c -> c.getPremium() == null ? 0L : c.getPremium()).sum()))
                .sorted(Comparator.comparingLong(VipParDimensionItem::primeTotale).reversed())
                .toList();
    }

    private List<CompteFidelite> comptesVip(List<CompteFidelite> comptes) {
        return comptes.stream().filter(c -> c.getStatutVip() == StatutVip.VIP_ACTIF).toList();
    }

    private long primeSignee(CompteFidelite compte) {
        if (compte.getInsured() == null) {
            return 0L;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(compte.getInsured().getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .mapToLong(c -> c.getPremium() == null ? 0L : c.getPremium())
                .sum();
    }

    private void requireAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canViewAll(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux profils ADMIN/MARKETING/MANAGER");
        }
    }

    private String insuredName(CompteFidelite compte) {
        if (compte.getInsured() == null) {
            return null;
        }
        return safe(() -> compte.getInsured().getPrenoms() + " " + compte.getInsured().getNom());
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElseGet(() -> safe(user::getUsername));
    }

    private String displayNamePartenaire(Partenaire partenaire) {
        if (partenaire == null) {
            return null;
        }
        return safe(() -> {
            String prenoms = partenaire.getPrenoms();
            return prenoms == null || prenoms.isBlank() ? partenaire.getNom() : partenaire.getNom() + " " + prenoms;
        });
    }
}
