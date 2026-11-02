package ci.assurzen.services;

import ci.assurzen.entities.BeneficiaireParrainage;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Parrainage;
import ci.assurzen.entities.RecompenseParrainage;
import ci.assurzen.entities.RegleRecompenseParrainage;
import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.repositories.RecompenseParrainageRepository;
import ci.assurzen.repositories.RegleRecompenseParrainageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Applique les RegleRecompenseParrainage actives d'un programme a un parrainage devenu eligible, et
 * reverse les recompenses deja attribuees quand un parrainage est annule apres coup (ex : contrat du
 * filleul resilie). Volontairement independant de CompteFidelite/points de fidelite existants - la
 * recompense de parrainage reste sa propre ligne, pas une double-ecriture dans un autre ledger.
 */
@Service
@RequiredArgsConstructor
public class RecompenseParrainageEngine {

    private final RegleRecompenseParrainageRepository regleRecompenseParrainageRepository;
    private final RecompenseParrainageRepository recompenseParrainageRepository;

    public int genererRecompenses(Parrainage parrainage) {
        List<RegleRecompenseParrainage> regles = regleRecompenseParrainageRepository
                .findByProgrammeIdAndActifTrueOrderByPrioriteAsc(parrainage.getProgramme().getId());

        int nombre = 0;
        for (RegleRecompenseParrainage regle : regles) {
            if (regle.getPrimeMinimum() != null && !primeSuffisante(parrainage, regle.getPrimeMinimum())) {
                continue;
            }
            if (regle.getBeneficiaire() == BeneficiaireParrainage.PARRAIN || regle.getBeneficiaire() == BeneficiaireParrainage.LES_DEUX) {
                creerRecompense(parrainage, regle, parrainage.getParrain());
                nombre++;
            }
            if (regle.getBeneficiaire() == BeneficiaireParrainage.FILLEUL || regle.getBeneficiaire() == BeneficiaireParrainage.LES_DEUX) {
                if (parrainage.getFilleul() != null) {
                    creerRecompense(parrainage, regle, parrainage.getFilleul());
                    nombre++;
                }
            }
        }
        return nombre;
    }

    public int reverserRecompenses(Parrainage parrainage, String motif) {
        List<RecompenseParrainage> aReverser = recompenseParrainageRepository.findByParrainageId(parrainage.getId()).stream()
                .filter(r -> r.getStatut() == StatutRecompenseParrainage.APPROUVEE || r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE
                        || r.getStatut() == StatutRecompenseParrainage.EN_ATTENTE || r.getStatut() == StatutRecompenseParrainage.ELIGIBLE)
                .toList();

        for (RecompenseParrainage recompense : aReverser) {
            recompense.setStatut(StatutRecompenseParrainage.REVERSEE);
            recompense.setDateAnnulation(Instant.now());
            recompense.setMotifAnnulation(motif);
            recompenseParrainageRepository.save(recompense);
        }
        return aReverser.size();
    }

    private void creerRecompense(Parrainage parrainage, RegleRecompenseParrainage regle, Insured beneficiaire) {
        Long montant = calculerMontant(parrainage, regle);

        RecompenseParrainage recompense = RecompenseParrainage.builder()
                .parrainage(parrainage)
                .regle(regle)
                .beneficiaire(beneficiaire)
                .typeRecompense(regle.getTypeRecompense())
                .statut(StatutRecompenseParrainage.EN_ATTENTE)
                .montant(montant)
                .pourcentage(regle.getPourcentage())
                .points(regle.getPoints())
                .description(regle.getNom())
                .dateEligibilite(Instant.now())
                .build();
        recompenseParrainageRepository.save(recompense);
    }

    private Long calculerMontant(Parrainage parrainage, RegleRecompenseParrainage regle) {
        if (regle.getPourcentage() != null && parrainage.getContrat() != null && parrainage.getContrat().getPremium() != null) {
            long calcule = Math.round(parrainage.getContrat().getPremium() * regle.getPourcentage() / 100.0);
            if (regle.getPlafondRecompense() != null) {
                calcule = Math.min(calcule, regle.getPlafondRecompense());
            }
            return calcule;
        }
        return regle.getMontantFixe();
    }

    private boolean primeSuffisante(Parrainage parrainage, Long primeMinimum) {
        return parrainage.getContrat() != null && parrainage.getContrat().getPremium() != null
                && parrainage.getContrat().getPremium() >= primeMinimum;
    }
}
