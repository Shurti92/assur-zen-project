package ci.assurzen.services;

import ci.assurzen.dto.DashboardAssureResponse;
import ci.assurzen.dto.DashboardPeriodeResponse;
import ci.assurzen.entities.TypeClient;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.projection.PeriodeCount;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * KPI assures (module "KPI Dashboard", §10/§11/§12/§13). Alimente GET /dashboard/assures. Agregation
 * SQL (InsuredRepository/ContractRepository), jamais findAll()+stream().
 *
 * Insured n'a pas de createdAt propre - "nouveaux"/l'evolution s'appuient sur la date du PREMIER
 * contrat de l'assure (seule donnee temporelle disponible, limite du modele documentee des la Phase 1).
 * actifs/plusieursContrats/multiEquipes sont des etats courants (pas des flux dates) : leur comparaison
 * "periode precedente" reconstruit l'etat tel qu'il etait a la fin de la periode precedente
 * (countXxxAvant(borne)) plutot que de comparer "maintenant" a "maintenant" (qui donnerait toujours 0%).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssureKpiService {

    private static final ZoneId ZONE_METIER = ZoneId.of("Africa/Abidjan");

    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public DashboardAssureResponse getKpis(LocalDate dateDebut, LocalDate dateFin) {
        requireAdmin();
        validerPeriode(dateDebut, dateFin);
        log.info("Consultation dashboard assures, periode {} a {}", dateDebut, dateFin);

        ComparaisonPeriode.Periode precedente = ComparaisonPeriode.periodePrecedente(dateDebut, dateFin);
        Instant debut = debutJour(dateDebut);
        Instant fin = finJour(dateFin);
        Instant debutPrec = debutJour(precedente.debut());
        Instant finPrec = finJour(precedente.fin());

        DashboardAssureResponse.AssureKpis kpis = buildKpis(debut, fin, debutPrec, finPrec);
        DashboardAssureResponse.AssureRepartitions repartitions = buildRepartitions();
        List<DashboardAssureResponse.AssureEvolutionItem> evolution = buildEvolution(dateDebut, dateFin, debut, fin);

        return new DashboardAssureResponse(new DashboardPeriodeResponse(dateDebut, dateFin), kpis, repartitions, evolution);
    }

    private DashboardAssureResponse.AssureKpis buildKpis(Instant debut, Instant fin, Instant debutPrec, Instant finPrec) {
        long totalActuel = insuredRepository.count();
        long nouveaux = contractRepository.countNouveauxAssures(debut, fin);
        long nouveauxPrec = contractRepository.countNouveauxAssures(debutPrec, finPrec);
        // Insured n'est jamais historise : le total "a la periode precedente" se deduit du total actuel
        // moins les nouveaux de la periode courante (aucune suppression d'assure dans ce modele).
        long totalPrec = Math.max(0L, totalActuel - nouveaux);

        long actifs = contractRepository.countAssuresActifsAvant(fin, dateDeInstant(fin));
        long actifsPrec = contractRepository.countAssuresActifsAvant(finPrec, dateDeInstant(finPrec));
        long inactifs = Math.max(0L, totalActuel - actifs);
        long inactifsPrec = Math.max(0L, totalPrec - actifsPrec);

        long plusieursContrats = contractRepository.countAssuresPlusieursContratsAvant(fin);
        long plusieursContratsPrec = contractRepository.countAssuresPlusieursContratsAvant(finPrec);
        long multiEquipes = contractRepository.countAssuresMultiEquipesAvant(fin);
        long multiEquipesPrec = contractRepository.countAssuresMultiEquipesAvant(finPrec);

        double moyenneContrats = contractRepository.moyenneContratsParAssureActifAvant(fin);
        double moyenneContratsPrec = contractRepository.moyenneContratsParAssureActifAvant(finPrec);

        return new DashboardAssureResponse.AssureKpis(
                ComparaisonPeriode.deLong(totalActuel, totalPrec),
                ComparaisonPeriode.deLong(nouveaux, nouveauxPrec),
                ComparaisonPeriode.deLong(actifs, actifsPrec),
                ComparaisonPeriode.deLong(inactifs, inactifsPrec),
                ComparaisonPeriode.deLong(plusieursContrats, plusieursContratsPrec),
                ComparaisonPeriode.deLong(multiEquipes, multiEquipesPrec),
                ComparaisonPeriode.deDouble(moyenneContrats, moyenneContratsPrec),
                ComparaisonPeriode.deDouble(taux(nouveaux, totalPrec), taux(nouveauxPrec, Math.max(0L, totalPrec - nouveauxPrec))),
                ComparaisonPeriode.deDouble(taux(multiEquipes, actifs), taux(multiEquipesPrec, actifsPrec))
        );
    }

    /** Non filtrees par periode - portrait structurel du portefeuille assures actuel (§12), coherent
     * avec l'absence de createdAt sur Insured (cf. javadoc DashboardAssureResponse.AssureRepartitions). */
    private DashboardAssureResponse.AssureRepartitions buildRepartitions() {
        List<DashboardAssureResponse.ParVilleItem> parVille = insuredRepository.repartitionParVille().stream()
                .map(l -> new DashboardAssureResponse.ParVilleItem(l.getLibelle(), l.getTotal()))
                .toList();
        List<DashboardAssureResponse.ParTypeClientItem> parType = insuredRepository.repartitionParTypeClient().stream()
                .map(l -> new DashboardAssureResponse.ParTypeClientItem(TypeClient.valueOf(l.getLibelle()), l.getTotal()))
                .toList();
        return new DashboardAssureResponse.AssureRepartitions(parVille, parType);
    }

    private List<DashboardAssureResponse.AssureEvolutionItem> buildEvolution(LocalDate dateDebut, LocalDate dateFin, Instant debut, Instant fin) {
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        List<PeriodeCount> nouveauxParPeriode;
        if (jours <= 31) {
            nouveauxParPeriode = contractRepository.evolutionNouveauxAssuresParJour(debut, fin);
        } else if (jours <= 180) {
            nouveauxParPeriode = contractRepository.evolutionNouveauxAssuresParSemaine(debut, fin);
        } else {
            nouveauxParPeriode = contractRepository.evolutionNouveauxAssuresParMois(debut, fin);
        }

        // totalAssures cumule = assures deja "connus" (premier contrat) avant le debut de la fenetre +
        // somme progressive des nouveaux point par point. Base et increments viennent tous les deux du
        // meme proxy contrat (pas de melange avec insuredRepository.count(), qui ne mesure pas la meme
        // chose - cf. javadoc de classe).
        long curseur = contractRepository.countAssuresConnusAvant(debut.minusNanos(1));
        List<DashboardAssureResponse.AssureEvolutionItem> resultat = new java.util.ArrayList<>();
        for (PeriodeCount p : nouveauxParPeriode) {
            curseur += p.getTotal();
            resultat.add(new DashboardAssureResponse.AssureEvolutionItem(p.getPeriode(), p.getTotal(), curseur));
        }
        return resultat;
    }

    private double taux(long numerateur, long denominateur) {
        return denominateur <= 0 ? 0d : numerateur * 100.0 / denominateur;
    }

    private LocalDate dateDeInstant(Instant instant) {
        return instant.atZone(ZONE_METIER).toLocalDate();
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
