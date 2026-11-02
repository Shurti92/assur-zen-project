package ci.assurzen.services;

import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Couvre le moteur de parsing/validation du nouvel import de vehicules (Phase 2 du module Flotte) -
 * template a colonnes fixes (pas de systeme de profils de mapping configurables comme l'import
 * Satisfaction, cf. memoire de session). Classe sans etat, testable sans mock. */
class VehiculeImportParserTest {

    private InputStream csv(String contenu) {
        return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parseCsv_fichierValide_produitLesLignesAttendues() throws IOException {
        String contenu = "immatriculation,type,marque,modele,numero_chassis,date_mise_en_circulation,puissance_fiscale,couleur,statut\n"
                + "CI-1234-AA,AUTO,Toyota,Corolla,CH123,2022-05-10,7,Blanc,ACTIF\n"
                + "CI-5678-BB,MOTO,Yamaha,MT07,,,,,\n";

        List<LigneVehiculeBrute> lignes = VehiculeImportParser.parseCsv(csv(contenu));

        assertEquals(2, lignes.size());
        assertEquals(2, lignes.get(0).ligneNumero());
        assertEquals("CI-1234-AA", lignes.get(0).valeurs().get("immatriculation"));
        assertEquals(3, lignes.get(1).ligneNumero());
    }

    @Test
    void parseCsv_entetesEnCasseDifferente_estAccepte() throws IOException {
        String contenu = "Immatriculation,TYPE,Marque,Modele\nCI-1234-AA,AUTO,Toyota,Corolla\n";

        List<LigneVehiculeBrute> lignes = VehiculeImportParser.parseCsv(csv(contenu));

        assertEquals("CI-1234-AA", lignes.get(0).valeurs().get("immatriculation"));
    }

    @Test
    void parseXlsx_fichierValide_produitLesLignesAttendues() throws IOException {
        byte[] fichier = construireXlsx(
                new String[] { "immatriculation", "type", "marque", "modele" },
                new String[][] {
                        { "CI-1234-AA", "AUTO", "Toyota", "Corolla" },
                        { "CI-5678-BB", "MOTO", "Yamaha", "MT07" }
                }
        );

        List<LigneVehiculeBrute> lignes = VehiculeImportParser.parseXlsx(new ByteArrayInputStream(fichier));

        assertEquals(2, lignes.size());
        assertEquals("CI-1234-AA", lignes.get(0).valeurs().get("immatriculation"));
        assertEquals("MOTO", lignes.get(1).valeurs().get("type"));
    }

    @Test
    void analyser_champObligatoireManquant_estCompteEnErreur() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "AUTO", "marque", "", "modele", "Corolla"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(0, resultat.lignesValides().size());
        assertEquals(1, resultat.erreurs().size());
        assertEquals("marque", resultat.erreurs().get(0).colonne());
    }

    /** Immatriculation optionnelle depuis le 2026-09-13 (moto sans plaque encore attribuee) - une
     * cellule vide/absente n'est plus une erreur, et devient null (pas ""), meme raison que
     * numero_chassis. Deux lignes sans immatriculation dans le meme fichier ne doivent PAS se compter
     * en doublon l'une de l'autre (Set.add(null) ne reussirait qu'une fois). */
    @Test
    void analyser_immatriculationVide_estAccepteeCommeNullPasErreurNiDoublon() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("type", "MOTO", "marque", "Yamaha", "modele", "Crypton", "numero_chassis", "CH-MOTO-001")),
                new LigneVehiculeBrute(3, java.util.Map.of("type", "MOTO", "marque", "Yamaha", "modele", "Crypton", "numero_chassis", "CH-MOTO-002"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(2, resultat.lignesValides().size());
        assertEquals(0, resultat.duplicateRows());
        assertTrue(resultat.erreurs().isEmpty());
        assertEquals(null, resultat.lignesValides().get(0).immatriculation());
        assertEquals(null, resultat.lignesValides().get(1).immatriculation());
    }

    @Test
    void analyser_cylindreeValide_estCorrectementParsee() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("type", "MOTO", "marque", "Yamaha", "modele", "Crypton", "cylindree", "110"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(1, resultat.lignesValides().size());
        assertEquals(110, resultat.lignesValides().get(0).cylindree());
    }

    @Test
    void analyser_cylindreeInvalide_estCompteeEnErreur() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("type", "MOTO", "marque", "Yamaha", "modele", "Crypton", "cylindree", "abc"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(0, resultat.lignesValides().size());
        assertEquals(1, resultat.erreurs().size());
        assertEquals("cylindree", resultat.erreurs().get(0).colonne());
    }

    @Test
    void analyser_typeInvalide_estCompteEnErreur() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "CAMION", "marque", "Toyota", "modele", "Corolla"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(0, resultat.lignesValides().size());
        assertEquals(1, resultat.erreurs().size());
        assertEquals("type", resultat.erreurs().get(0).colonne());
    }

    @Test
    void analyser_dateInvalide_estCompteEnErreur() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "AUTO", "marque", "Toyota", "modele", "Corolla",
                        "date_mise_en_circulation", "10/05/2022"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(1, resultat.erreurs().size());
        assertEquals("date_mise_en_circulation", resultat.erreurs().get(0).colonne());
    }

    @Test
    void analyser_immatriculationDupliqueeDansLeFichier_estCompteeEnDoublonPasEnErreur() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "ci-1234-aa", "type", "AUTO", "marque", "Toyota", "modele", "Corolla")),
                new LigneVehiculeBrute(3, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "AUTO", "marque", "Toyota", "modele", "Corolla"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(1, resultat.lignesValides().size());
        assertEquals(1, resultat.duplicateRows());
        assertTrue(resultat.erreurs().isEmpty());
    }

    @Test
    void analyser_ligneValideComplete_statutParDefautEstActif() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "AUTO", "marque", "Toyota", "modele", "Corolla"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        LigneVehiculeValidee ligne = resultat.lignesValides().get(0);
        assertEquals(VehiculeStatut.ACTIF, ligne.statut());
        assertEquals(VehiculeType.AUTO, ligne.type());
    }

    @Test
    void analyser_dateEtPuissanceValides_sontCorrectementParsees() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1234-AA", "type", "AUTO", "marque", "Toyota", "modele", "Corolla",
                        "date_mise_en_circulation", "2022-05-10", "puissance_fiscale", "7"))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        LigneVehiculeValidee ligne = resultat.lignesValides().get(0);
        assertEquals(LocalDate.of(2022, 5, 10), ligne.dateMiseEnCirculation());
        assertEquals(7, ligne.puissanceFiscale());
    }

    /** Regression : deux vehicules avec numero_chassis="" (chaine vide, pas absente) dans le fichier
     * ne doivent jamais violer uk_vehicule_chassis - MySQL/MariaDB traite deux chaines vides comme un
     * doublon reel (contrairement a deux NULL). Bug reel constate en test E2E. */
    @Test
    void analyser_numeroChassisVide_estNormaliseANullPasChaineVide() {
        List<LigneVehiculeBrute> lignes = List.of(
                new LigneVehiculeBrute(2, java.util.Map.of("immatriculation", "CI-1111-AA", "type", "AUTO", "marque", "Toyota", "modele", "Corolla", "numero_chassis", "")),
                new LigneVehiculeBrute(3, java.util.Map.of("immatriculation", "CI-2222-BB", "type", "AUTO", "marque", "Toyota", "modele", "Corolla", "numero_chassis", ""))
        );

        ResultatAnalyseImportVehicules resultat = VehiculeImportParser.analyser(lignes);

        assertEquals(2, resultat.lignesValides().size());
        assertEquals(null, resultat.lignesValides().get(0).numeroChassis());
        assertEquals(null, resultat.lignesValides().get(1).numeroChassis());
    }

    private byte[] construireXlsx(String[] entetes, String[][] lignes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vehicules");
            Row header = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                header.createCell(i).setCellValue(entetes[i]);
            }
            for (int r = 0; r < lignes.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < lignes[r].length; c++) {
                    row.createCell(c).setCellValue(lignes[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
