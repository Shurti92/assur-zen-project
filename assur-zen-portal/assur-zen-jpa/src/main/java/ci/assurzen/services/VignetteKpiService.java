package ci.assurzen.services;

import ci.assurzen.dto.DashboardPeriodeResponse;
import ci.assurzen.dto.DashboardVignetteResponse;
import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.projection.LibelleCount;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * KPI vignettes moto (module "KPI Dashboard", §4/§5/§6). Alimente GET /dashboard/vignettes. Agregation
 * SQL (DemandeVignetteMotoRepository), jamais findAll()+stream() - cf. §17 du contrat.
 *
 * Les taux de transformation/edition/livraison (§4 Performance) sont mathematiquement corrects mais
 * resteront a 0% tant que le workflow paiement/edition/livraison n'a pas de service derriere (seuls
 * BROUILLON/EN_ATTENTE_PAIEMENT/ANNULEE/REJETEE sont atteignables aujourd'hui, cf. DemandeVignetteMotoService) -
 * ce n'est pas un bug, juste l'etat reel des donnees.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VignetteKpiService {

    private static final ZoneId ZONE_METIER = ZoneId.of("Africa/Abidjan");

    /** "Payee ou au-dela" : toute demande qui a au moins atteint le paiement, quel que soit son statut
     * actuel plus avance (EN_TRAITEMENT/EN_EDITION/EDITEE/EN_LIVRAISON/LIVREE en decoulent forcement). */
    private static final Set<StatutDemandeVignetteMoto> STATUTS_PAYEE_OU_APRES = Set.of(
            StatutDemandeVignetteMoto.PAYEE, StatutDemandeVignetteMoto.EN_TRAITEMENT, StatutDemandeVignetteMoto.EN_EDITION,
            StatutDemandeVignetteMoto.EDITEE, StatutDemandeVignetteMoto.EN_LIVRAISON, StatutDemandeVignetteMoto.LIVREE);

    private static final Set<StatutDemandeVignetteMoto> STATUTS_EDITEE_OU_APRES = Set.of(
            StatutDemandeVignetteMoto.EDITEE, StatutDemandeVignetteMoto.EN_LIVRAISON, StatutDemandeVignetteMoto.LIVREE);

    private final DemandeVignetteMotoRepository repository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public DashboardVignetteResponse getKpis(LocalDate dateDebut, LocalDate dateFin) {
        requireAdmin();
        validerPeriode(dateDebut, dateFin);
        log.info("Consultation dashboard vignettes, periode {} a {}", dateDebut, dateFin);

        ComparaisonPeriode.Periode precedente = ComparaisonPeriode.periodePrecedente(dateDebut, dateFin);
        Instant debut = debutJour(dateDebut);
        Instant fin = finJour(dateFin);
        Instant debutPrec = debutJour(precedente.debut());
        Instant finPrec = finJour(precedente.fin());

        DashboardVignetteResponse.VignetteKpis kpis = buildKpis(debut, fin, debutPrec, finPrec);
        DashboardVignetteResponse.VignetteRepartitions repartitions = buildRepartitions(debut, fin);
        List<DashboardVignetteResponse.VignetteEvolutionItem> evolution = buildEvolution(dateDebut, dateFin, debut, fin);

        return new DashboardVignetteResponse(new DashboardPeriodeResponse(dateDebut, dateFin), kpis, repartitions, evolution);
    }

    private DashboardVignetteResponse.VignetteKpis buildKpis(Instant debut, Instant fin, Instant debutPrec, Instant finPrec) {
        long nombreDemandes = repository.countByCreatedAtBetween(debut, fin);
        long nouvellesDemandes = repository.countByCreatedAtBetweenAndTypeDemande(debut, fin, TypeDemandeVignette.NOUVELLE_DEMANDE);
        long renouvellements = repository.countByCreatedAtBetweenAndTypeDemande(debut, fin, TypeDemandeVignette.RENOUVELLEMENT);
        long vignettesEditees = repository.countByCreatedAtBetweenAndStatutIn(debut, fin, STATUTS_EDITEE_OU_APRES);
        long vignettesLivrees = repository.countByCreatedAtBetweenAndStatut(debut, fin, StatutDemandeVignetteMoto.LIVREE);
        long demandesRejetees = repository.countByCreatedAtBetweenAndStatut(debut, fin, StatutDemandeVignetteMoto.REJETEE);
        long demandesAnnulees = repository.countByCreatedAtBetweenAndStatut(debut, fin, StatutDemandeVignetteMoto.ANNULEE);
        long demandesPayeesOuApres = repository.countByCreatedAtBetweenAndStatutIn(debut, fin, STATUTS_PAYEE_OU_APRES);
        // "En attente" = pas encore conclue dans un sens ou l'autre (ni livree, ni rejetee, ni annulee).
        long demandesEnAttente = nombreDemandes - vignettesLivrees - demandesRejetees - demandesAnnulees;

        long nombreDemandesPrec = repository.countByCreatedAtBetween(debutPrec, finPrec);
        long nouvellesDemandesPrec = repository.countByCreatedAtBetweenAndTypeDemande(debutPrec, finPrec, TypeDemandeVignette.NOUVELLE_DEMANDE);
        long renouvellementsPrec = repository.countByCreatedAtBetweenAndTypeDemande(debutPrec, finPrec, TypeDemandeVignette.RENOUVELLEMENT);
        long vignettesEditeesPrec = repository.countByCreatedAtBetweenAndStatutIn(debutPrec, finPrec, STATUTS_EDITEE_OU_APRES);
        long vignettesLivreesPrec = repository.countByCreatedAtBetweenAndStatut(debutPrec, finPrec, StatutDemandeVignetteMoto.LIVREE);
        long demandesRejeteesPrec = repository.countByCreatedAtBetweenAndStatut(debutPrec, finPrec, StatutDemandeVignetteMoto.REJETEE);
        long demandesAnnuleesPrec = repository.countByCreatedAtBetweenAndStatut(debutPrec, finPrec, StatutDemandeVignetteMoto.ANNULEE);
        long demandesPayeesOuApresPrec = repository.countByCreatedAtBetweenAndStatutIn(debutPrec, finPrec, STATUTS_PAYEE_OU_APRES);
        long demandesEnAttentePrec = nombreDemandesPrec - vignettesLivreesPrec - demandesRejeteesPrec - demandesAnnuleesPrec;

        BigDecimal montantVignettes = repository.sumMontantVignette(debut, fin);
        BigDecimal totalFraisOperation = repository.sumFraisOperation(debut, fin);
        BigDecimal totalFraisLivraison = repository.sumFraisLivraison(debut, fin);
        BigDecimal totalFraisAccessoires = repository.sumFraisAccessoires(debut, fin);
        BigDecimal chiffreAffaires = repository.sumMontantTotal(debut, fin);
        BigDecimal panierMoyen = panierMoyen(chiffreAffaires, nombreDemandes);

        BigDecimal montantVignettesPrec = repository.sumMontantVignette(debutPrec, finPrec);
        BigDecimal totalFraisOperationPrec = repository.sumFraisOperation(debutPrec, finPrec);
        BigDecimal totalFraisLivraisonPrec = repository.sumFraisLivraison(debutPrec, finPrec);
        BigDecimal totalFraisAccessoiresPrec = repository.sumFraisAccessoires(debutPrec, finPrec);
        BigDecimal chiffreAffairesPrec = repository.sumMontantTotal(debutPrec, finPrec);
        BigDecimal panierMoyenPrec = panierMoyen(chiffreAffairesPrec, nombreDemandesPrec);

        return new DashboardVignetteResponse.VignetteKpis(
                ComparaisonPeriode.deLong(nombreDemandes, nombreDemandesPrec),
                ComparaisonPeriode.deLong(nouvellesDemandes, nouvellesDemandesPrec),
                ComparaisonPeriode.deLong(renouvellements, renouvellementsPrec),
                ComparaisonPeriode.deLong(vignettesEditees, vignettesEditeesPrec),
                ComparaisonPeriode.deLong(vignettesLivrees, vignettesLivreesPrec),
                ComparaisonPeriode.deLong(demandesEnAttente, demandesEnAttentePrec),
                ComparaisonPeriode.deLong(demandesRejetees, demandesRejeteesPrec),
                ComparaisonPeriode.deLong(demandesAnnulees, demandesAnnuleesPrec),

                ComparaisonPeriode.deBigDecimal(montantVignettes, montantVignettesPrec),
                ComparaisonPeriode.deBigDecimal(totalFraisOperation, totalFraisOperationPrec),
                ComparaisonPeriode.deBigDecimal(totalFraisLivraison, totalFraisLivraisonPrec),
                ComparaisonPeriode.deBigDecimal(totalFraisAccessoires, totalFraisAccessoiresPrec),
                ComparaisonPeriode.deBigDecimal(chiffreAffaires, chiffreAffairesPrec),
                ComparaisonPeriode.deBigDecimal(panierMoyen, panierMoyenPrec),

                ComparaisonPeriode.deDouble(taux(demandesPayeesOuApres, nombreDemandes), taux(demandesPayeesOuApresPrec, nombreDemandesPrec)),
                ComparaisonPeriode.deDouble(taux(vignettesEditees, demandesPayeesOuApres), taux(vignettesEditeesPrec, demandesPayeesOuApresPrec)),
                ComparaisonPeriode.deDouble(taux(vignettesLivrees, vignettesEditees), taux(vignettesLivreesPrec, vignettesEditeesPrec)),
                ComparaisonPeriode.deDouble(taux(demandesRejetees, nombreDemandes), taux(demandesRejeteesPrec, nombreDemandesPrec)),
                ComparaisonPeriode.deDouble(taux(demandesAnnulees, nombreDemandes), taux(demandesAnnuleesPrec, nombreDemandesPrec)),
                null
        );
    }

    private DashboardVignetteResponse.VignetteRepartitions buildRepartitions(Instant debut, Instant fin) {
        List<DashboardVignetteResponse.ParTypeDemandeItem> parType = repository.repartitionParTypeDemande(debut, fin).stream()
                .map(l -> new DashboardVignetteResponse.ParTypeDemandeItem(TypeDemandeVignette.valueOf(l.getLibelle()), l.getTotal()))
                .toList();
        List<DashboardVignetteResponse.ParCylindreeItem> parCylindree = repository.repartitionParCylindree(debut, fin).stream()
                .map(l -> new DashboardVignetteResponse.ParCylindreeItem(CategorieCylindree.valueOf(l.getLibelle()), l.getTotal()))
                .toList();
        List<DashboardVignetteResponse.ParAgeMotoItem> parAge = repository.repartitionParAgeMoto(debut, fin).stream()
                .map(l -> new DashboardVignetteResponse.ParAgeMotoItem(CategorieAgeMoto.valueOf(l.getLibelle()), l.getTotal()))
                .toList();
        List<DashboardVignetteResponse.ParZoneLivraisonItem> parZone = repository.repartitionParZoneLivraison(debut, fin).stream()
                .map(l -> new DashboardVignetteResponse.ParZoneLivraisonItem(l.getLibelle(), l.getTotal()))
                .toList();
        List<DashboardVignetteResponse.VignetteParStatutItem> parStatut = repository.repartitionParStatut(debut, fin).stream()
                .map(l -> new DashboardVignetteResponse.VignetteParStatutItem(StatutDemandeVignetteMoto.valueOf(l.getLibelle()), l.getTotal()))
                .toList();

        return new DashboardVignetteResponse.VignetteRepartitions(parType, parCylindree, parAge, parZone, parStatut);
    }

    /** Granularite adaptee a la duree de la periode (§6) : jusqu'a 31 jours -> JOUR (l'exemple donne est
     * "7 jours -> JOUR"), jusqu'a environ 6 mois -> SEMAINE, au-dela -> MOIS (l'autre exemple donne).
     * Seuils non precises dans le contrat, choisis ici - a ajuster si le frontend a besoin d'autre chose. */
    private List<DashboardVignetteResponse.VignetteEvolutionItem> buildEvolution(LocalDate dateDebut, LocalDate dateFin, Instant debut, Instant fin) {
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
                .map(p -> new DashboardVignetteResponse.VignetteEvolutionItem(p.getPeriode(), p.getTotal(), p.getMontant()))
                .toList();
    }

    private BigDecimal panierMoyen(BigDecimal chiffreAffaires, long nombreDemandes) {
        if (nombreDemandes == 0 || chiffreAffaires == null) {
            return BigDecimal.ZERO;
        }
        return chiffreAffaires.divide(BigDecimal.valueOf(nombreDemandes), 2, RoundingMode.HALF_UP);
    }

    private double taux(long numerateur, long denominateur) {
        return denominateur == 0 ? 0d : numerateur * 100.0 / denominateur;
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
