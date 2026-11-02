package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Les identifiants Java ne peuvent pas commencer par un chiffre - @JsonProperty force la valeur JSON
 * exacte attendue par le contrat frontend ("1_4_ANS", pas "AGE_1_4_ANS"). Aucune tranche n'est
 * definie par le tarificateur pour une moto de moins d'1 an : VignetteMotoAgeCalculator classe ce
 * cas en AGE_1_4_ANS (hypothese posee faute de tranche dediee, a confirmer si besoin).
 */
public enum CategorieAgeMoto {
    @JsonProperty("1_4_ANS")
    AGE_1_4_ANS,
    @JsonProperty("5_10_ANS")
    AGE_5_10_ANS,
    @JsonProperty("11_ANS_ET_PLUS")
    AGE_11_ANS_ET_PLUS
}
