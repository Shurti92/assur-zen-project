package ci.assurzen.services;

import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parsing + validation de fichiers d'import de vehicules (CSV/XLSX), template a colonnes fixes
 * (type/marque/modele obligatoires, immatriculation/numero_chassis/date_mise_en_circulation/
 * puissance_fiscale/cylindree/couleur/statut optionnels - immatriculation devenue optionnelle le
 * 2026-09-13 pour l'enregistrement de motos sans plaque encore attribuee) - pas de systeme de
 * profils de mapping configurables (contrairement a l'import Satisfaction) : le vehicule a un jeu
 * de champs fixe, le proprietaire n'est pas une colonne du fichier (implicite : le client de la
 * flotte ciblee). Classe sans etat, sans acces base de donnees - uniquement lecture/validation de
 * format.
 */
final class VehiculeImportParser {

    static final List<String> COLONNES_ATTENDUES = List.of(
            "immatriculation", "type", "marque", "modele", "numero_chassis",
            "date_mise_en_circulation", "puissance_fiscale", "cylindree", "couleur", "statut"
    );

    private VehiculeImportParser() {
    }

    static List<LigneVehiculeBrute> parseCsv(InputStream inputStream) throws IOException {
        List<LigneVehiculeBrute> lignes = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            int ligneNumero = 1;
            for (CSVRecord record : parser) {
                ligneNumero++;
                Map<String, String> valeurs = new HashMap<>();
                record.toMap().forEach((cle, valeur) -> valeurs.put(normaliserColonne(cle), valeur));
                lignes.add(new LigneVehiculeBrute(ligneNumero, valeurs));
            }
        }
        return lignes;
    }

    static List<LigneVehiculeBrute> parseXlsx(InputStream inputStream) throws IOException {
        List<LigneVehiculeBrute> lignes = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return lignes;
            }
            Map<Integer, String> colonnes = new HashMap<>();
            DataFormatter formatter = new DataFormatter();
            for (Cell cell : headerRow) {
                colonnes.put(cell.getColumnIndex(), normaliserColonne(formatter.formatCellValue(cell)));
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row, formatter)) {
                    continue;
                }
                Map<String, String> valeurs = new HashMap<>();
                for (Map.Entry<Integer, String> entree : colonnes.entrySet()) {
                    Cell cell = row.getCell(entree.getKey());
                    valeurs.put(entree.getValue(), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                lignes.add(new LigneVehiculeBrute(i + 1, valeurs));
            }
        }
        return lignes;
    }

    private static boolean estLigneVide(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private static String normaliserColonne(String colonne) {
        return colonne == null ? "" : colonne.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    static ResultatAnalyseImportVehicules analyser(List<LigneVehiculeBrute> lignes) {
        List<LigneVehiculeValidee> valides = new ArrayList<>();
        List<LigneVehiculeErreur> erreurs = new ArrayList<>();
        Set<String> immatriculationsVues = new HashSet<>();
        int duplicateRows = 0;

        for (LigneVehiculeBrute ligne : lignes) {
            List<LigneVehiculeErreur> erreursLigne = new ArrayList<>();
            LigneVehiculeValidee validee = validerLigne(ligne, erreursLigne);
            if (!erreursLigne.isEmpty()) {
                erreurs.addAll(erreursLigne);
                continue;
            }
            // Immatriculation optionnelle (2026-09-13) : ne pas deduplicer sur une valeur absente,
            // sinon la 2e ligne sans plaque du meme fichier serait a tort signalee comme doublon de
            // la 1ere (Set.add(null) ne reussit qu'une seule fois) - meme categorie de bug deja
            // rencontree sur numero_chassis (cf. commentaire de validerLigne()).
            if (validee.immatriculation() != null && !immatriculationsVues.add(validee.immatriculation())) {
                duplicateRows++;
                continue;
            }
            valides.add(validee);
        }
        return new ResultatAnalyseImportVehicules(lignes.size(), valides, erreurs, duplicateRows);
    }

    private static LigneVehiculeValidee validerLigne(LigneVehiculeBrute ligne, List<LigneVehiculeErreur> erreursOut) {
        int n = ligne.ligneNumero();

        // Optionnelle depuis le 2026-09-13 (moto sans plaque encore attribuee) - "" (cellule vide du
        // fichier) DOIT devenir null, pas une chaine vide, meme raison que numero_chassis plus bas :
        // la contrainte unique en base traite deux chaines vides comme un doublon reel, mais tolere
        // plusieurs NULL.
        String immatriculationRaw = valeur(ligne, "immatriculation");
        String immatriculation = StringUtils.hasText(immatriculationRaw) ? immatriculationRaw.toUpperCase(Locale.ROOT) : null;

        String typeRaw = valeur(ligne, "type");
        VehiculeType type = null;
        if (!StringUtils.hasText(typeRaw)) {
            erreursOut.add(new LigneVehiculeErreur(n, "type", typeRaw, "Type obligatoire (AUTO ou MOTO)"));
        } else {
            try {
                type = VehiculeType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                erreursOut.add(new LigneVehiculeErreur(n, "type", typeRaw, "Type invalide (attendu: AUTO ou MOTO)"));
            }
        }

        String marque = valeur(ligne, "marque");
        if (!StringUtils.hasText(marque)) {
            erreursOut.add(new LigneVehiculeErreur(n, "marque", marque, "Marque obligatoire"));
        }

        String modele = valeur(ligne, "modele");
        if (!StringUtils.hasText(modele)) {
            erreursOut.add(new LigneVehiculeErreur(n, "modele", modele, "Modele obligatoire"));
        }

        // "" (chaine vide) != null pour la contrainte unique uk_vehicule_chassis en base - MySQL/MariaDB
        // traite deux chaines vides comme un doublon reel (contrairement a deux NULL, autorises), donc
        // une cellule vide du fichier DOIT devenir null, pas "" (bug reel constate en test E2E : le 2e
        // vehicule sans numero de chassis dans le fichier faisait echouer tout l'import sur ce conflit).
        String numeroChassisRaw = valeur(ligne, "numero_chassis");
        String numeroChassis = StringUtils.hasText(numeroChassisRaw) ? numeroChassisRaw : null;

        LocalDate dateMiseEnCirculation = null;
        String dateRaw = valeur(ligne, "date_mise_en_circulation");
        if (StringUtils.hasText(dateRaw)) {
            try {
                dateMiseEnCirculation = LocalDate.parse(dateRaw);
            } catch (DateTimeParseException e) {
                erreursOut.add(new LigneVehiculeErreur(n, "date_mise_en_circulation", dateRaw, "Date invalide (format attendu: AAAA-MM-JJ)"));
            }
        }

        Integer puissanceFiscale = null;
        String puissanceRaw = valeur(ligne, "puissance_fiscale");
        if (StringUtils.hasText(puissanceRaw)) {
            try {
                puissanceFiscale = Integer.valueOf(puissanceRaw.trim());
            } catch (NumberFormatException e) {
                erreursOut.add(new LigneVehiculeErreur(n, "puissance_fiscale", puissanceRaw, "Puissance fiscale invalide (nombre entier attendu)"));
            }
        }

        Integer cylindree = null;
        String cylindreeRaw = valeur(ligne, "cylindree");
        if (StringUtils.hasText(cylindreeRaw)) {
            try {
                cylindree = Integer.valueOf(cylindreeRaw.trim());
            } catch (NumberFormatException e) {
                erreursOut.add(new LigneVehiculeErreur(n, "cylindree", cylindreeRaw, "Cylindree invalide (nombre entier attendu)"));
            }
        }

        String couleurRaw = valeur(ligne, "couleur");
        String couleur = StringUtils.hasText(couleurRaw) ? couleurRaw : null;

        VehiculeStatut statut = VehiculeStatut.ACTIF;
        String statutRaw = valeur(ligne, "statut");
        if (StringUtils.hasText(statutRaw)) {
            try {
                statut = VehiculeStatut.valueOf(statutRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                erreursOut.add(new LigneVehiculeErreur(n, "statut", statutRaw, "Statut invalide (attendu: ACTIF, HORS_SERVICE ou VENDU)"));
            }
        }

        if (!erreursOut.isEmpty()) {
            return null;
        }
        return new LigneVehiculeValidee(n, immatriculation, type, marque, modele, numeroChassis,
                dateMiseEnCirculation, puissanceFiscale, cylindree, couleur, statut);
    }

    private static String valeur(LigneVehiculeBrute ligne, String cle) {
        String v = ligne.valeurs().get(cle);
        return v == null ? null : v.trim();
    }
}
