package ci.assurzen.services;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Couvre §21 points 1-6 (les 6 combinaisons age/cylindree) + les cas limites de bornes d'age et
 * l'hypothese posee pour une moto de moins d'1 an (§6 du contrat vignette moto).
 */
class VignetteMotoCategorisationTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 1, 1);

    // ---------------------------------------------------------------- calculerAge() : les 6 combinaisons

    @Test
    void calculerAge_moto1An_estClasseeEn1_4Ans() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2025, 1, 1), REFERENCE);
        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_moto4Ans_estClasseeEn1_4Ans_borneHaute() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2022, 1, 1), REFERENCE);
        assertEquals(4, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_moto5Ans_estClasseeEn5_10Ans_borneBasse() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2021, 1, 1), REFERENCE);
        assertEquals(5, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_5_10_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_moto10Ans_estClasseeEn5_10Ans_borneHaute() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2016, 1, 1), REFERENCE);
        assertEquals(10, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_5_10_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_moto11Ans_estClasseeEn11AnsEtPlus_borneBasse() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2015, 1, 1), REFERENCE);
        assertEquals(11, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_11_ANS_ET_PLUS, resultat.categorieAge());
    }

    @Test
    void calculerAge_moto26Ans_estClasseeEn11AnsEtPlus() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2000, 1, 1), REFERENCE);
        assertEquals(CategorieAgeMoto.AGE_11_ANS_ET_PLUS, resultat.categorieAge());
    }

    @Test
    void calculerAge_motoDeMoinsDunAn_estClasseeEn1_4Ans_hypothesePoseeFauteDeTrancheDediee() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2025, 12, 1), REFERENCE);
        assertEquals(0, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_exactement4AnsEtQuelquesMois_resteEn1_4Ans_pasEncoreBasculeeEn5_10() {
        // 2026-01-01 - 2021-11-01 = 4 ans et 2 mois : encore 4 ans revolus, pas 5.
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2021, 11, 1), REFERENCE);
        assertEquals(4, resultat.ageAnnees());
        assertEquals(CategorieAgeMoto.AGE_1_4_ANS, resultat.categorieAge());
    }

    @Test
    void calculerAge_ageRenvoyeEstLeNombreDanneesRevoluesExact() {
        var resultat = VignetteMotoCategorisation.calculerAge(LocalDate.of(2020, 5, 15), LocalDate.of(2026, 9, 11));
        assertEquals(6, resultat.ageAnnees());
    }

    // ---------------------------------------------------------------- categoriserCylindree()

    @Test
    void categoriserCylindree_109cm3_estMoins125() {
        assertEquals(CategorieCylindree.MOINS_125_CM3, VignetteMotoCategorisation.categoriserCylindree(109));
    }

    @Test
    void categoriserCylindree_exactement125cm3_est125EtPlus_borneInclusiveHaute() {
        assertEquals(CategorieCylindree.CM3_125_ET_PLUS, VignetteMotoCategorisation.categoriserCylindree(125));
    }

    @Test
    void categoriserCylindree_200cm3_est125EtPlus() {
        assertEquals(CategorieCylindree.CM3_125_ET_PLUS, VignetteMotoCategorisation.categoriserCylindree(200));
    }
}
