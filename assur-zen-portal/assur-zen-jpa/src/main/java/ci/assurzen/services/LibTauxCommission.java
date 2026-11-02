package ci.assurzen.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unite d'une commission configuree (produits-commission-taux, produits-commission-commercial) :
 * "%" = pourcentage de la prime, "FCFA" = montant fixe. ContractsService ne reconnait un pourcentage
 * que si le libelle vaut exactement "%" - tout autre libelle libre ("10%", "Taux"...) etait
 * silencieusement traite comme un montant fixe (decision utilisateur du 2026-09-25 : valeurs fermees).
 */
final class LibTauxCommission {

    static final String POURCENTAGE = "%";
    static final String MONTANT_FIXE = "FCFA";

    private LibTauxCommission() {
    }

    static void verifier(String libTauxCommission) {
        if (!POURCENTAGE.equals(libTauxCommission) && !MONTANT_FIXE.equals(libTauxCommission)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "libTauxCommission doit valoir \"%\" (pourcentage de la prime) ou \"FCFA\" (montant fixe), recu : \""
                            + libTauxCommission + "\"");
        }
    }
}
