package ci.assurzen.services;

import ci.assurzen.dto.CompteFideliteResponse;
import ci.assurzen.entities.Avantage;
import ci.assurzen.entities.CompteFidelite;
import ci.assurzen.entities.CompteFideliteAvantage;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.entities.FidelisationHistorique;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.MouvementFideliteType;
import ci.assurzen.entities.PalierFidelite;
import ci.assurzen.entities.RegleFidelite;
import ci.assurzen.entities.RegleFideliteChamp;
import ci.assurzen.entities.SegmentRuleOperator;
import ci.assurzen.entities.StatutEcheance;
import ci.assurzen.entities.StatutVip;
import ci.assurzen.mapper.CompteFideliteMapper;
import ci.assurzen.repositories.AvantageRepository;
import ci.assurzen.repositories.CompteFideliteAvantageRepository;
import ci.assurzen.repositories.CompteFideliteRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.EcheanceRepository;
import ci.assurzen.repositories.EnqueteSatisfactionRepository;
import ci.assurzen.repositories.FidelisationHistoriqueRepository;
import ci.assurzen.repositories.PalierFideliteRepository;
import ci.assurzen.repositories.ReclamationRepository;
import ci.assurzen.repositories.RegleFideliteRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Moteur de calcul du score de fidelite / statut VIP. Aucune regle metier n'est codee en dur : la
 * liste des RegleFidelite actives (champ/operateur/valeur/poids) pilote entierement le calcul. Le
 * seul code fige est la liste finie des CHAMPS mesurables (RegleFideliteChamp) et comment les
 * resoudre a partir du domaine metier — inevitable dans tout moteur de ce type (Guidewire/Duck Creek
 * inclus), la CONFIGURATION (seuils/poids/activation) reste elle 100% pilotee par la table.
 *
 * Reutilise FideliteService.resolveInsured/checkAccess/getOrCreateCompte (methodes package-private)
 * plutot que de les dupliquer.
 */
@Service
@RequiredArgsConstructor
public class ScoreFideliteService {

    private final CompteFideliteRepository compteFideliteRepository;
    private final RegleFideliteRepository regleFideliteRepository;
    private final PalierFideliteRepository palierFideliteRepository;
    private final ContractRepository contractRepository;
    private final SinistreRepository sinistreRepository;
    private final ReclamationRepository reclamationRepository;
    private final EnqueteSatisfactionRepository enqueteSatisfactionRepository;
    private final EcheanceRepository echeanceRepository;
    private final AvantageRepository avantageRepository;
    private final CompteFideliteAvantageRepository compteFideliteAvantageRepository;
    private final FidelisationHistoriqueRepository fidelisationHistoriqueRepository;
    private final FideliteService fideliteService;
    private final CompteFideliteMapper compteFideliteMapper;
    private final AuthService authService;

    @Transactional
    public CompteFideliteResponse recalculer(Long insuredId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }

        Insured insured = fideliteService.resolveInsured(insuredId);
        CompteFidelite compte = fideliteService.getOrCreateCompte(insured);

        long score = calculerScore(insured);
        Long ancienScore = compte.getScoreFidelite();
        StatutVip ancienStatut = compte.getStatutVip();
        PalierFidelite ancienPalier = compte.getPalier();

        PalierFidelite nouveauPalier = resoudrePalier(score);
        compte.setScoreFidelite(score);
        compte.setPalier(nouveauPalier);

        boolean estVip = nouveauPalier != null && nouveauPalier.isEstVip();
        if (estVip && ancienStatut != StatutVip.VIP_ACTIF) {
            compte.setStatutVip(StatutVip.VIP_ACTIF);
            compte.setDateEntreeVip(Instant.now());
            compte.setDateSortieVip(null);
        } else if (!estVip && ancienStatut == StatutVip.VIP_ACTIF) {
            compte.setStatutVip(StatutVip.VIP_SORTI);
            compte.setDateSortieVip(Instant.now());
        }

        compte = compteFideliteRepository.save(compte);

        boolean palierChange = !Objects.equals(idDe(ancienPalier), idDe(nouveauPalier));
        if (estVip && ancienStatut != StatutVip.VIP_ACTIF) {
            enregistrerHistorique(compte, MouvementFideliteType.ENTREE_VIP,
                    "Entree VIP - palier " + libelleDe(nouveauPalier) + " (score " + score + ")");
        } else if (!estVip && ancienStatut == StatutVip.VIP_ACTIF) {
            enregistrerHistorique(compte, MouvementFideliteType.SORTIE_VIP, "Sortie VIP - score retombe a " + score);
        } else if (palierChange) {
            enregistrerHistorique(compte, MouvementFideliteType.CHANGEMENT_NIVEAU,
                    "Changement de palier: " + libelleDe(ancienPalier) + " -> " + libelleDe(nouveauPalier));
        } else if (!Objects.equals(ancienScore, score)) {
            enregistrerHistorique(compte, MouvementFideliteType.SCORE_RECALCULE, "Score recalcule: " + score + " (etait " + ancienScore + ")");
        }

        gererAvantagesAutomatiques(compte, nouveauPalier);

        return compteFideliteMapper.toResponse(compte);
    }

    private long calculerScore(Insured insured) {
        List<RegleFidelite> regles = regleFideliteRepository.findByActifTrueOrderByOrdreAsc();
        long score = 0;
        for (RegleFidelite regle : regles) {
            Double valeurReelle = resoudreValeur(regle.getChamp(), insured);
            if (matches(regle.getOperateur(), valeurReelle, regle.getValeur())) {
                score += regle.getPoids();
            }
        }
        return score;
    }

    private Double resoudreValeur(RegleFideliteChamp champ, Insured insured) {
        return switch (champ) {
            case ANCIENNETE_ANNEES -> (double) ancienneteAnnees(insured);
            case NOMBRE_CONTRATS -> (double) contratsSignes(insured).size();
            case PRIME_ANNUELLE -> primeAnnuelle(insured);
            case NOMBRE_SINISTRES -> (double) sinistreRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).size();
            case NPS -> npsMoyen(insured);
            case RECLAMATIONS -> (double) reclamationRepository.findByInsuredId(insured.getId()).size();
            case ECHEANCES_IMPAYEES -> (double) echeancesImpayees(insured);
        };
    }

    private List<Contract> contratsSignes(Insured insured) {
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .toList();
    }

    private long ancienneteAnnees(Insured insured) {
        LocalDate dateReference = contratsSignes(insured).stream()
                .map(Contract::getDateEffet)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (dateReference == null) {
            return 0;
        }
        return ChronoUnit.YEARS.between(dateReference, LocalDate.now());
    }

    private double primeAnnuelle(Insured insured) {
        return contratsSignes(insured).stream()
                .mapToLong(c -> c.getPremium() == null ? 0L : c.getPremium())
                .sum();
    }

    private Double npsMoyen(Insured insured) {
        List<EnqueteSatisfaction> repondues = enqueteSatisfactionRepository.findByInsuredId(insured.getId()).stream()
                .filter(e -> e.getStatut() == EnqueteStatut.REPONDUE && e.getScoreNps() != null)
                .toList();
        return repondues.isEmpty() ? null : repondues.stream().mapToInt(EnqueteSatisfaction::getScoreNps).average().orElse(0);
    }

    private long echeancesImpayees(Insured insured) {
        LocalDate today = LocalDate.now();
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .flatMap(c -> echeanceRepository.findByContractIdOrderByNumeroEcheanceAsc(c.getId()).stream())
                .filter(e -> e.getStatut() == StatutEcheance.EN_ATTENTE && e.getDateEcheance() != null && e.getDateEcheance().isBefore(today))
                .count();
    }

    private boolean matches(SegmentRuleOperator operateur, Double valeurReelle, String valeurAttendue) {
        if (valeurReelle == null) {
            return false;
        }
        double attendue;
        try {
            attendue = Double.parseDouble(valeurAttendue.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        int cmp = Double.compare(valeurReelle, attendue);
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

    private PalierFidelite resoudrePalier(long score) {
        return palierFideliteRepository
                .findFirstByActiveTrueAndSeuilPointsLessThanEqualOrderBySeuilPointsDesc(score)
                .orElse(null);
    }

    private void gererAvantagesAutomatiques(CompteFidelite compte, PalierFidelite nouveauPalier) {
        List<CompteFideliteAvantage> actifs = compteFideliteAvantageRepository.findByCompteFideliteIdAndActifTrue(compte.getId());
        for (CompteFideliteAvantage attribution : actifs) {
            Avantage avantage = attribution.getAvantage();
            if (avantage.getPalier() != null && !Objects.equals(idDe(avantage.getPalier()), idDe(nouveauPalier))) {
                attribution.setActif(false);
                attribution.setDateExpiration(Instant.now());
                compteFideliteAvantageRepository.save(attribution);
            }
        }

        if (nouveauPalier == null) {
            return;
        }
        List<Avantage> avantagesDuPalier = avantageRepository.findByPalierIdAndActiveTrue(nouveauPalier.getId());
        for (Avantage avantage : avantagesDuPalier) {
            boolean dejaActif = compteFideliteAvantageRepository
                    .findByCompteFideliteIdAndAvantageIdAndActifTrue(compte.getId(), avantage.getId())
                    .isPresent();
            if (!dejaActif) {
                CompteFideliteAvantage attribution = CompteFideliteAvantage.builder()
                        .compteFidelite(compte)
                        .avantage(avantage)
                        .dateAttribution(Instant.now())
                        .actif(true)
                        .build();
                compteFideliteAvantageRepository.save(attribution);
                enregistrerHistorique(compte, MouvementFideliteType.AVANTAGE_ATTRIBUE, "Avantage attribue: " + avantage.getNom());
            }
        }
    }

    private void enregistrerHistorique(CompteFidelite compte, MouvementFideliteType type, String motif) {
        FidelisationHistorique mouvement = FidelisationHistorique.builder()
                .compteFidelite(compte)
                .typeMouvement(type)
                .points(0L)
                .soldeApres(compte.getSoldePoints())
                .motif(motif)
                .build();
        fidelisationHistoriqueRepository.save(mouvement);
    }

    private Long idDe(PalierFidelite palier) {
        return palier == null ? null : palier.getId();
    }

    private String libelleDe(PalierFidelite palier) {
        return palier == null ? "aucun" : palier.getLibelle();
    }
}
