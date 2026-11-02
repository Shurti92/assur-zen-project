package ci.assurzen.services;

import ci.assurzen.dto.ParrainageDashboardOverviewResponse;
import ci.assurzen.dto.ParrainageMensuelItem;
import ci.assurzen.dto.ParrainageParStatutItem;
import ci.assurzen.dto.ParrainageTopParrainItem;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Parrainage;
import ci.assurzen.entities.RecompenseParrainage;
import ci.assurzen.entities.StatutParrainage;
import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.repositories.ParrainageRepository;
import ci.assurzen.repositories.RecompenseParrainageRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
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
import java.util.stream.Collectors;

/**
 * Dashboard analytique lecture-seule du module Parrainage. Meme convention que VipDashboardService :
 * agregation en memoire sur les repositories existants, pas de CRUD ici.
 */
@Service
@RequiredArgsConstructor
public class ParrainageDashboardService {

    private final ParrainageRepository parrainageRepository;
    private final RecompenseParrainageRepository recompenseParrainageRepository;
    private final AuthService authService;

    public ParrainageDashboardOverviewResponse overview() {
        requireAccess();
        List<Parrainage> tous = parrainageRepository.findAll();
        List<RecompenseParrainage> recompenses = recompenseParrainageRepository.findAll();

        long total = tous.size();
        long eligibles = tous.stream().filter(p -> p.getDateEligibilite() != null).count();
        long convertis = tous.stream().filter(p -> p.getStatut() == StatutParrainage.RECOMPENSE_ATTRIBUEE).count();
        double taux = total == 0 ? 0.0 : (convertis * 100.0) / total;

        long recompensesAttribuees = recompenses.stream().filter(r -> r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE).count();
        long montantTotal = recompenses.stream()
                .filter(r -> r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE)
                .mapToLong(r -> r.getMontant() == null ? 0L : r.getMontant())
                .sum();

        long parrainsActifs = tous.stream()
                .map(p -> p.getParrain() == null ? null : p.getParrain().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        return new ParrainageDashboardOverviewResponse(total, eligibles, convertis, taux, recompensesAttribuees, montantTotal, parrainsActifs);
    }

    public List<ParrainageParStatutItem> parStatut() {
        requireAccess();
        Map<StatutParrainage, Long> parStatut = parrainageRepository.findAll().stream()
                .collect(Collectors.groupingBy(Parrainage::getStatut, Collectors.counting()));

        return List.of(StatutParrainage.values()).stream()
                .map(s -> new ParrainageParStatutItem(s, parStatut.getOrDefault(s, 0L)))
                .toList();
    }

    /** Tendance sur les 12 derniers mois calendaires (parrainages crees / convertis, mois vides inclus). */
    public List<ParrainageMensuelItem> evolutionMensuelle() {
        requireAccess();
        List<Parrainage> tous = parrainageRepository.findAll();

        Map<YearMonth, List<Parrainage>> creesParMois = tous.stream()
                .filter(p -> p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.getCreatedAt().atZone(ZoneOffset.UTC))));

        Map<YearMonth, List<Parrainage>> convertisParMois = tous.stream()
                .filter(p -> p.getStatut() == StatutParrainage.RECOMPENSE_ATTRIBUEE && p.getDateCompletion() != null)
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.getDateCompletion().atZone(ZoneOffset.UTC))));

        YearMonth moisCourant = YearMonth.now();
        List<ParrainageMensuelItem> resultat = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mois = moisCourant.minusMonths(i);
            long nombre = creesParMois.getOrDefault(mois, List.of()).size();
            long convertis = convertisParMois.getOrDefault(mois, List.of()).size();
            resultat.add(new ParrainageMensuelItem(mois.toString(), nombre, convertis));
        }
        return resultat;
    }

    public List<ParrainageTopParrainItem> topParrains(int limite) {
        requireAccess();
        List<Parrainage> tous = parrainageRepository.findAll();

        Map<Insured, List<Parrainage>> parParrain = tous.stream()
                .filter(p -> p.getParrain() != null)
                .collect(Collectors.groupingBy(Parrainage::getParrain));

        return parParrain.entrySet().stream()
                .map(entry -> {
                    Insured parrain = entry.getKey();
                    List<Parrainage> parrainages = entry.getValue();
                    long convertis = parrainages.stream().filter(p -> p.getStatut() == StatutParrainage.RECOMPENSE_ATTRIBUEE).count();
                    long recompensesTotal = recompenseParrainageRepository.findByBeneficiaireId(parrain.getId()).stream()
                            .filter(r -> r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE)
                            .mapToLong(r -> r.getMontant() == null ? 0L : r.getMontant())
                            .sum();
                    return new ParrainageTopParrainItem(parrain.getId(), insuredName(parrain), parrainages.size(), convertis, recompensesTotal);
                })
                .sorted(Comparator.comparingLong(ParrainageTopParrainItem::nombreConvertis).reversed()
                        .thenComparing(Comparator.comparingLong(ParrainageTopParrainItem::nombreParrainages).reversed()))
                .limit(limite)
                .toList();
    }

    private void requireAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canViewAll(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux profils ADMIN/MARKETING/MANAGER");
        }
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }
}
