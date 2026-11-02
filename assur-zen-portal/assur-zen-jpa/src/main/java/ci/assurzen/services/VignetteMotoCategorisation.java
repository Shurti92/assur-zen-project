package ci.assurzen.services;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;

import java.time.LocalDate;
import java.time.Period;

/**
 * Fonction metier dediee (§6 du contrat vignette moto) : jamais demander l'age d'une moto a
 * l'utilisateur, toujours le calculer a partir de datePremiereMiseCirculation et d'une date de
 * reference explicite - pure, sans dependance, testable independamment de "aujourd'hui".
 *
 * Hypothese posee (aucune tranche fournie par le tarificateur pour une moto de moins d'1 an) : une
 * moto d'age 0 est classee AGE_1_4_ANS.
 */
public final class VignetteMotoCategorisation {

    private static final int SEUIL_CYLINDREE_CM3 = 125;

    private VignetteMotoCategorisation() {
    }

    public record ResultatAge(int ageAnnees, CategorieAgeMoto categorieAge) {
    }

    public static ResultatAge calculerAge(LocalDate datePremiereMiseCirculation, LocalDate dateReference) {
        int ageAnnees = Period.between(datePremiereMiseCirculation, dateReference).getYears();
        CategorieAgeMoto categorieAge;
        if (ageAnnees <= 4) {
            categorieAge = CategorieAgeMoto.AGE_1_4_ANS;
        } else if (ageAnnees <= 10) {
            categorieAge = CategorieAgeMoto.AGE_5_10_ANS;
        } else {
            categorieAge = CategorieAgeMoto.AGE_11_ANS_ET_PLUS;
        }
        return new ResultatAge(ageAnnees, categorieAge);
    }

    public static CategorieCylindree categoriserCylindree(int cylindreeCm3) {
        return cylindreeCm3 < SEUIL_CYLINDREE_CM3 ? CategorieCylindree.MOINS_125_CM3 : CategorieCylindree.CM3_125_ET_PLUS;
    }
}
