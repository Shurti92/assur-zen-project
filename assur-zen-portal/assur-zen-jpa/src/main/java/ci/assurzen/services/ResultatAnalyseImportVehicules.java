package ci.assurzen.services;

import java.util.List;

record ResultatAnalyseImportVehicules(
        int totalRows,
        List<LigneVehiculeValidee> lignesValides,
        List<LigneVehiculeErreur> erreurs,
        int duplicateRows
) {
}
