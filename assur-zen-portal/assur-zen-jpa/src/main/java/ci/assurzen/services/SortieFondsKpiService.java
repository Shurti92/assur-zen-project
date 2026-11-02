package ci.assurzen.services;

import ci.assurzen.dto.DashboardPeriodeResponse;
import ci.assurzen.dto.DashboardSortieFondsResponse;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.PaiementRepository;
import ci.assurzen.repositories.projection.LibelleCountMontant;
import ci.assurzen.repositories.projection.PeriodeCountMontant;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * KPI sorties de fonds (module "KPI Dashboard", §7/§8/§9). Alimente GET /dashboard/sorties-fonds.
 * Agregation SQL (FundOutflowRepository), jamais findAll()+stream().
 *
 * "Validees"/"executees" ont deux lectures possibles selon qu'on parle d'un volume instantane (combien
 * de sorties sont actuellement dans cet etat) ou d'un taux (combien de sorties ayant ete validees ont
 * fini executees). Les KPI de volume utilisent l'etat courant (snapshot), le taux d'execution utilise
 * un ensemble cumulatif "validee ou apres" - meme methodologie que les taux funnel de VignetteKpiService
 * (sinon le denominateur "validees" exclurait les sorties deja executees, ce qui n'aurait pas de sens
 * pour un taux de conversion). Choix documente ici plutot qu'invente silencieusement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SortieFondsKpiService {

    private static final ZoneId ZONE_METIER = ZoneId.of("Africa/Abidjan");

    private static final Set<FundOutflowStatus> S_EN_ATTENTE =
            Set.of(FundOutflowStatus.DRAFT, FundOutflowStatus.SUBMITTED, FundOutflowStatus.UNDER_REVIEW);
    private static final Set<FundOutflowStatus> S_VALIDEE_SNAPSHOT =
            Set.of(FundOutflowStatus.APPROVED, FundOutflowStatus.AUTHORIZED, FundOutflowStatus.PROCESSING);
    private static final Set<FundOutflowStatus> S_EXECUTEE =
            Set.of(FundOutflowStatus.DISBURSED, FundOutflowStatus.PARTIALLY_REPAID, FundOutflowStatus.REPAID, FundOutflowStatus.CLOSED);
    private static final Set<FundOutflowStatus> S_VALIDEE_OU_APRES;

    static {
        S_VALIDEE_OU_APRES = java.util.stream.Stream.concat(S_VALIDEE_SNAPSHOT.stream(), S_EXECUTEE.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private final FundOutflowRepository repository;
    private final PaiementRepository paiementRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public DashboardSortieFondsResponse getKpis(LocalDate dateDebut, LocalDate dateFin) {
        requireAdmin();
        validerPeriode(dateDebut, dateFin);
        log.info("Consultation dashboard sorties de fonds, periode {} a {}", dateDebut, dateFin);

        ComparaisonPeriode.Periode precedente = ComparaisonPeriode.periodePrecedente(dateDebut, dateFin);
        Instant debut = debutJour(dateDebut);
        Instant fin = finJour(dateFin);
        Instant debutPrec = debutJour(precedente.debut());
        Instant finPrec = finJour(precedente.fin());

        DashboardSortieFondsResponse.SortieFondsKpis kpis = buildKpis(dateDebut, dateFin, debut, fin, precedente.debut(), precedente.fin(), debutPrec, finPrec);
        DashboardSortieFondsResponse.SortieFondsRepartitions repartitions = buildRepartitions(debut, fin);
        List<DashboardSortieFondsResponse.SortieFondsEvolutionItem> evolution = buildEvolution(dateDebut, dateFin, debut, fin);

        return new DashboardSortieFondsResponse(new DashboardPeriodeResponse(dateDebut, dateFin), kpis, repartitions, evolution);
    }

    private DashboardSortieFondsResponse.SortieFondsKpis buildKpis(LocalDate dateDebut, LocalDate dateFin, Instant debut, Instant fin,
                                                          LocalDate dateDebutPrec, LocalDate dateFinPrec, Instant debutPrec, Instant finPrec) {
        long total = repository.countByCreatedAtBetween(debut, fin);
        long enAttente = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, S_EN_ATTENTE);
        long validees = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, S_VALIDEE_SNAPSHOT);
        long executees = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, S_EXECUTEE);
        long rejetees = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, Set.of(FundOutflowStatus.REJECTED));
        long annulees = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, Set.of(FundOutflowStatus.CANCELLED));
        long valideesOuApres = repository.countByCreatedAtBetweenAndStatusIn(debut, fin, S_VALIDEE_OU_APRES);

        long totalPrec = repository.countByCreatedAtBetween(debutPrec, finPrec);
        long enAttentePrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, S_EN_ATTENTE);
        long valideesPrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, S_VALIDEE_SNAPSHOT);
        long executeesPrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, S_EXECUTEE);
        long rejeteesPrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, Set.of(FundOutflowStatus.REJECTED));
        long annuleesPrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, Set.of(FundOutflowStatus.CANCELLED));
        long valideesOuApresPrec = repository.countByCreatedAtBetweenAndStatusIn(debutPrec, finPrec, S_VALIDEE_OU_APRES);

        BigDecimal montantTotal = repository.sumAmount(debut, fin);
        BigDecimal montantEnAttente = repository.sumAmountByStatusIn(debut, fin, S_EN_ATTENTE);
        BigDecimal montantValide = repository.sumAmountByStatusIn(debut, fin, S_VALIDEE_SNAPSHOT);
        BigDecimal montantExecute = repository.sumAmountByStatusIn(debut, fin, S_EXECUTEE);
        BigDecimal montantRejete = repository.sumAmountByStatusIn(debut, fin, Set.of(FundOutflowStatus.REJECTED));
        BigDecimal montantAnnule = repository.sumAmountByStatusIn(debut, fin, Set.of(FundOutflowStatus.CANCELLED));
        BigDecimal montantMoyen = repository.avgAmount(debut, fin);
        BigDecimal montantMax = repository.maxAmount(debut, fin);

        BigDecimal montantTotalPrec = repository.sumAmount(debutPrec, finPrec);
        BigDecimal montantEnAttentePrec = repository.sumAmountByStatusIn(debutPrec, finPrec, S_EN_ATTENTE);
        BigDecimal montantValidePrec = repository.sumAmountByStatusIn(debutPrec, finPrec, S_VALIDEE_SNAPSHOT);
        BigDecimal montantExecutePrec = repository.sumAmountByStatusIn(debutPrec, finPrec, S_EXECUTEE);
        BigDecimal montantRejetePrec = repository.sumAmountByStatusIn(debutPrec, finPrec, Set.of(FundOutflowStatus.REJECTED));
        BigDecimal montantAnnulePrec = repository.sumAmountByStatusIn(debutPrec, finPrec, Set.of(FundOutflowStatus.CANCELLED));
        BigDecimal montantMoyenPrec = repository.avgAmount(debutPrec, finPrec);

        Double delaiMoyen = repository.delaiMoyenTraitementJours(debut, fin);
        Double delaiMoyenPrec = repository.delaiMoyenTraitementJours(debutPrec, finPrec);

        BigDecimal totalEncaissements = BigDecimal.valueOf(paiementRepository.sumMontant(dateDebut, dateFin));
        BigDecimal totalEncaissementsPrec = BigDecimal.valueOf(paiementRepository.sumMontant(dateDebutPrec, dateFinPrec));
        BigDecimal solde = totalEncaissements.subtract(montantTotal == null ? BigDecimal.ZERO : montantTotal);
        BigDecimal soldePrec = totalEncaissementsPrec.subtract(montantTotalPrec == null ? BigDecimal.ZERO : montantTotalPrec);

        return new DashboardSortieFondsResponse.SortieFondsKpis(
                ComparaisonPeriode.deLong(total, totalPrec),
                ComparaisonPeriode.deLong(enAttente, enAttentePrec),
                ComparaisonPeriode.deLong(validees, valideesPrec),
                ComparaisonPeriode.deLong(executees, executeesPrec),
                ComparaisonPeriode.deLong(rejetees, rejeteesPrec),
                ComparaisonPeriode.deLong(annulees, annuleesPrec),

                ComparaisonPeriode.deBigDecimal(montantTotal, montantTotalPrec),
                ComparaisonPeriode.deBigDecimal(montantEnAttente, montantEnAttentePrec),
                ComparaisonPeriode.deBigDecimal(montantValide, montantValidePrec),
                ComparaisonPeriode.deBigDecimal(montantExecute, montantExecutePrec),
                ComparaisonPeriode.deBigDecimal(montantRejete, montantRejetePrec),
                ComparaisonPeriode.deBigDecimal(montantAnnule, montantAnnulePrec),
                ComparaisonPeriode.deBigDecimal(montantMoyen, montantMoyenPrec),
                montantMax,

                ComparaisonPeriode.deDouble(taux(executees, valideesOuApres), taux(executeesPrec, valideesOuApresPrec)),
                ComparaisonPeriode.deDouble(taux(rejetees, total), taux(rejeteesPrec, totalPrec)),
                delaiMoyen == null || delaiMoyenPrec == null ? null : ComparaisonPeriode.deDouble(delaiMoyen, delaiMoyenPrec),

                ComparaisonPeriode.deBigDecimal(totalEncaissements, totalEncaissementsPrec),
                ComparaisonPeriode.deBigDecimal(solde, soldePrec),
                ComparaisonPeriode.deDouble(
                        ratio(montantTotal, totalEncaissements), ratio(montantTotalPrec, totalEncaissementsPrec))
        );
    }

    private DashboardSortieFondsResponse.SortieFondsRepartitions buildRepartitions(Instant debut, Instant fin) {
        List<DashboardSortieFondsResponse.ParTypeItem> parType = repository.repartitionParType(debut, fin).stream()
                .map(l -> new DashboardSortieFondsResponse.ParTypeItem(l.getLibelle(), l.getTotal(), l.getMontant()))
                .toList();
        List<DashboardSortieFondsResponse.ParBeneficiaireItem> parBeneficiaire = repository.repartitionParBeneficiaire(debut, fin).stream()
                .map((LibelleCountMontant l) -> new DashboardSortieFondsResponse.ParBeneficiaireItem(l.getLibelle(), l.getTotal(), l.getMontant()))
                .toList();
        List<DashboardSortieFondsResponse.SortieFondsParStatutItem> parStatut = repository.repartitionParStatut(debut, fin).stream()
                .map(l -> new DashboardSortieFondsResponse.SortieFondsParStatutItem(l.getLibelle(), l.getTotal(), l.getMontant()))
                .toList();

        return new DashboardSortieFondsResponse.SortieFondsRepartitions(parType, parBeneficiaire, parStatut);
    }

    private List<DashboardSortieFondsResponse.SortieFondsEvolutionItem> buildEvolution(LocalDate dateDebut, LocalDate dateFin, Instant debut, Instant fin) {
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        List<PeriodeCountMontant> points;
        if (jours <= 31) {
            points = repository.evolutionParJour(debut, fin);
        } else if (jours <= 180) {
            points = repository.evolutionParSemaine(debut, fin);
        } else {
            points = repository.evolutionParMois(debut, fin);
        }
        return points.stream()
                .map(p -> new DashboardSortieFondsResponse.SortieFondsEvolutionItem(p.getPeriode(), p.getTotal(), p.getMontant()))
                .toList();
    }

    private double taux(long numerateur, long denominateur) {
        return denominateur == 0 ? 0d : numerateur * 100.0 / denominateur;
    }

    private double ratio(BigDecimal sorties, BigDecimal encaissements) {
        if (encaissements == null || encaissements.signum() == 0 || sorties == null) {
            return 0d;
        }
        return sorties.doubleValue() * 100.0 / encaissements.doubleValue();
    }

    private Instant debutJour(LocalDate date) {
        return date.atStartOfDay(ZONE_METIER).toInstant();
    }

    private Instant finJour(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE_METIER).toInstant().minusNanos(1);
    }

    private void validerPeriode(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_PERIODE_INVALIDE: dateDebut et dateFin sont obligatoires");
        }
        if (dateDebut.isAfter(dateFin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_PERIODE_INVALIDE: dateDebut ne peut pas etre posterieure a dateFin");
        }
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }
}
