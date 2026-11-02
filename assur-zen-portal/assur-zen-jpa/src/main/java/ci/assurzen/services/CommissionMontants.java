package ci.assurzen.services;

/** Formatage des montants de commission dans les SMS et notifications (regroupement par milliers avec
 * un espace simple, pas le NBSP de Locale.FRANCE - plus sur pour un SMS GSM-7). */
final class CommissionMontants {

    private CommissionMontants() {
    }

    static String formater(long montant) {
        String brut = String.valueOf(montant);
        StringBuilder resultat = new StringBuilder();
        int compteur = 0;
        for (int i = brut.length() - 1; i >= 0; i--) {
            resultat.append(brut.charAt(i));
            compteur++;
            if (compteur % 3 == 0 && i != 0 && brut.charAt(i - 1) != '-') {
                resultat.append(' ');
            }
        }
        return resultat.reverse().toString();
    }
}
