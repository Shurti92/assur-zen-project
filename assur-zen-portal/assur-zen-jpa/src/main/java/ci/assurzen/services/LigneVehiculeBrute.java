package ci.assurzen.services;

import java.util.Map;

/** Ligne brute d'un fichier d'import de vehicules, avant validation - valeurs indexees par nom de
 * colonne normalise (minuscule, sans espace de bord), quel que soit le format source (CSV/XLSX). */
record LigneVehiculeBrute(int ligneNumero, Map<String, String> valeurs) {
}
