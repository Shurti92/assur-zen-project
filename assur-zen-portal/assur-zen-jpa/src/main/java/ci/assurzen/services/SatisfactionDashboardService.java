package ci.assurzen.services;

import ci.assurzen.dto.NpsBreakdownResponse;
import ci.assurzen.dto.ReclamationStatistiquesResponse;
import ci.assurzen.dto.RepartitionNotesResponse;
import ci.assurzen.dto.SatisfactionMensuelleResponse;
import ci.assurzen.dto.SatisfactionOverviewResponse;
import ci.assurzen.dto.SatisfactionParDimensionItem;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.EnqueteSatisfaction;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.EnqueteSatisfactionRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard analytique lecture-seule de la Satisfaction Client : agrege EnqueteSatisfaction (+ Contract
 * pour les axes produit/gestionnaire/courtier) et delegue la partie reclamations a
 * ReclamationService.statistiques() plutot que de dupliquer ce calcul. Pas de CRUD ici, uniquement des
 * GET. Reserve a ADMIN/MARKETING/MANAGER (pas de scoping par salesRep : c'est une vue de pilotage globale).
 */
@Service
@RequiredArgsConstructor
public class SatisfactionDashboardService {

    private final EnqueteSatisfactionRepository enqueteSatisfactionRepository;
    private final ProfileRepository profileRepository;
    private final ReclamationService reclamationService;
    private final AuthService authService;

    public SatisfactionOverviewResponse overview() {
        requireAccess();
        List<EnqueteSatisfaction> enquetes = enqueteSatisfactionRepository.findAll();
        List<EnqueteSatisfaction> repondues = repondues(enquetes);

        long enquetesEnvoyees = enquetes.stream().filter(e -> e.getStatut() != EnqueteStatut.ECHEC_ENVOI).count();
        long reponses = repondues.size();
        Double tauxReponse = enquetesEnvoyees == 0 ? null : reponses * 100.0 / enquetesEnvoyees;

        ReclamationStatistiquesResponse reclamationsStats = reclamationService.statistiques();

        return new SatisfactionOverviewResponse(
                satisfactionMoyenne(repondues),
                calculerNps(repondues),
                enquetesEnvoyees,
                reponses,
                tauxReponse,
                reclamationsStats.ouvertes(),
                reclamationsStats.resolues(),
                reclamationsStats.delaiMoyenResolutionHeures());
    }

    public NpsBreakdownResponse nps() {
        requireAccess();
        List<EnqueteSatisfaction> repondues = repondues(enqueteSatisfactionRepository.findAll());

        long promoteurs = repondues.stream().filter(e -> e.getScoreNps() >= 9).count();
        long passifs = repondues.stream().filter(e -> e.getScoreNps() >= 7 && e.getScoreNps() <= 8).count();
        long detracteurs = repondues.stream().filter(e -> e.getScoreNps() <= 6).count();

        return new NpsBreakdownResponse(promoteurs, passifs, detracteurs, repondues.size(), calculerNps(repondues));
    }

    /** Tendance sur les 12 derniers mois calendaires (mois sans reponse inclus, avec des valeurs nulles/0). */
    public List<SatisfactionMensuelleResponse> mensuel() {
        requireAccess();
        List<EnqueteSatisfaction> repondues = repondues(enqueteSatisfactionRepository.findAll());

        Map<YearMonth, List<EnqueteSatisfaction>> parMois = repondues.stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getDateReponse().atZone(ZoneOffset.UTC))));

        YearMonth moisCourant = YearMonth.now();
        List<SatisfactionMensuelleResponse> resultat = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mois = moisCourant.minusMonths(i);
            List<EnqueteSatisfaction> duMois = parMois.getOrDefault(mois, List.of());
            resultat.add(new SatisfactionMensuelleResponse(mois.toString(), satisfactionMoyenne(duMois), calculerNps(duMois), duMois.size()));
        }
        return resultat;
    }

    public RepartitionNotesResponse repartitionNotes() {
        requireAccess();
        List<EnqueteSatisfaction> repondues = repondues(enqueteSatisfactionRepository.findAll());

        Map<Integer, Long> parNote = new LinkedHashMap<>();
        for (int note = 0; note <= 10; note++) {
            parNote.put(note, 0L);
        }
        repondues.forEach(e -> parNote.merge(e.getScoreNps(), 1L, Long::sum));

        return new RepartitionNotesResponse(parNote, repondues.size());
    }

    public List<SatisfactionParDimensionItem> parProduit() {
        requireAccess();
        return parDimension(e -> e.getContrat() == null ? null : e.getContrat().getProduct(),
                Produit::getId, Produit::getNomProduit);
    }

    public List<SatisfactionParDimensionItem> parGestionnaire() {
        requireAccess();
        return parDimension(e -> e.getContrat() == null ? null : e.getContrat().getSalesRep(),
                User::getId, this::displayName);
    }

    public List<SatisfactionParDimensionItem> parCourtier() {
        requireAccess();
        return parDimension(e -> e.getContrat() == null ? null : e.getContrat().getPartenaire(),
                Partenaire::getId, this::displayNamePartenaire);
    }

    public ReclamationStatistiquesResponse reclamations() {
        requireAccess();
        return reclamationService.statistiques();
    }

    /** Export CSV consolide de l'ensemble du dashboard, meme convention que ContractsService.exportCsv (BOM + ';'). */
    public byte[] exportCsv() {
        requireAccess();

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Tableau de bord Satisfaction Client - Export du ").append(formatDateHeure(Instant.now())).append("\n\n");

        SatisfactionOverviewResponse overview = overview();
        csv.append("KPIs generaux\n");
        csv.append("Indicateur;Valeur\n");
        csv.append("Satisfaction moyenne;").append(CsvUtils.field(overview.satisfactionMoyenne())).append('\n');
        csv.append("NPS;").append(CsvUtils.field(overview.nps())).append('\n');
        csv.append("Enquetes envoyees;").append(CsvUtils.field(overview.enquetesEnvoyees())).append('\n');
        csv.append("Reponses;").append(CsvUtils.field(overview.reponses())).append('\n');
        csv.append("Taux de reponse (%);").append(CsvUtils.field(overview.tauxReponse())).append('\n');
        csv.append("Reclamations ouvertes;").append(CsvUtils.field(overview.reclamationsOuvertes())).append('\n');
        csv.append("Reclamations resolues;").append(CsvUtils.field(overview.reclamationsResolues())).append('\n');
        csv.append("Delai moyen de traitement (h);").append(CsvUtils.field(overview.delaiMoyenTraitementHeures())).append("\n\n");

        NpsBreakdownResponse nps = nps();
        csv.append("Repartition NPS\n");
        csv.append("Promoteurs;Passifs;Detracteurs;Total;NPS\n");
        csv.append(CsvUtils.field(nps.promoteurs())).append(';').append(CsvUtils.field(nps.passifs())).append(';')
                .append(CsvUtils.field(nps.detracteurs())).append(';').append(CsvUtils.field(nps.totalReponses())).append(';')
                .append(CsvUtils.field(nps.nps())).append("\n\n");

        csv.append("Tendance mensuelle (12 derniers mois)\n");
        csv.append("Mois;Satisfaction moyenne;NPS;Reponses\n");
        for (SatisfactionMensuelleResponse m : mensuel()) {
            csv.append(CsvUtils.field(m.mois())).append(';').append(CsvUtils.field(m.satisfactionMoyenne())).append(';')
                    .append(CsvUtils.field(m.nps())).append(';').append(CsvUtils.field(m.reponses())).append('\n');
        }
        csv.append('\n');

        csv.append("Repartition des notes\n");
        csv.append("Note;Nombre\n");
        for (Map.Entry<Integer, Long> entry : repartitionNotes().parNote().entrySet()) {
            csv.append(CsvUtils.field(entry.getKey())).append(';').append(CsvUtils.field(entry.getValue())).append('\n');
        }
        csv.append('\n');

        appendDimensionCsv(csv, "Satisfaction par produit", parProduit());
        appendDimensionCsv(csv, "Satisfaction par gestionnaire", parGestionnaire());
        appendDimensionCsv(csv, "Satisfaction par courtier", parCourtier());

        ReclamationStatistiquesResponse reclamations = reclamationService.statistiques();
        csv.append("Reclamations\n");
        csv.append("Total;Ouvertes;Resolues;Fermees;Creees automatiquement;Delai moyen de resolution (h)\n");
        csv.append(CsvUtils.field(reclamations.total())).append(';').append(CsvUtils.field(reclamations.ouvertes())).append(';')
                .append(CsvUtils.field(reclamations.resolues())).append(';').append(CsvUtils.field(reclamations.fermees())).append(';')
                .append(CsvUtils.field(reclamations.creeesAutomatiquement())).append(';')
                .append(CsvUtils.field(reclamations.delaiMoyenResolutionHeures())).append("\n\n");

        csv.append("Reclamations par statut\n");
        csv.append("Statut;Nombre\n");
        reclamations.parStatut().forEach((k, v) -> csv.append(CsvUtils.field(k)).append(';').append(CsvUtils.field(v)).append('\n'));

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendDimensionCsv(StringBuilder csv, String titre, List<SatisfactionParDimensionItem> items) {
        csv.append(titre).append('\n');
        csv.append("Nom;Satisfaction moyenne;NPS;Reponses\n");
        for (SatisfactionParDimensionItem item : items) {
            csv.append(CsvUtils.field(item.nom())).append(';').append(CsvUtils.field(item.satisfactionMoyenne())).append(';')
                    .append(CsvUtils.field(item.nps())).append(';').append(CsvUtils.field(item.reponses())).append('\n');
        }
        csv.append('\n');
    }

    /** Export PDF consolide du dashboard (OpenPDF), mêmes sections que exportCsv(). */
    public byte[] exportPdf() {
        requireAccess();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Tableau de bord Satisfaction Client", titleFont));
            document.add(new Paragraph("Exporte le " + formatDateHeure(Instant.now()), normalFont));
            document.add(Chunk.NEWLINE);

            SatisfactionOverviewResponse overview = overview();
            addPdfSection(document, "KPIs generaux", sectionFont, normalFont, new String[]{"Indicateur", "Valeur"}, List.of(
                    new String[]{"Satisfaction moyenne", fmt(overview.satisfactionMoyenne())},
                    new String[]{"NPS", fmt(overview.nps())},
                    new String[]{"Enquetes envoyees", fmt(overview.enquetesEnvoyees())},
                    new String[]{"Reponses", fmt(overview.reponses())},
                    new String[]{"Taux de reponse (%)", fmt(overview.tauxReponse())},
                    new String[]{"Reclamations ouvertes", fmt(overview.reclamationsOuvertes())},
                    new String[]{"Reclamations resolues", fmt(overview.reclamationsResolues())},
                    new String[]{"Delai moyen de traitement (h)", fmt(overview.delaiMoyenTraitementHeures())}));

            NpsBreakdownResponse nps = nps();
            addPdfSection(document, "Repartition NPS", sectionFont, normalFont,
                    new String[]{"Promoteurs", "Passifs", "Detracteurs", "Total", "NPS"},
                    List.<String[]>of(new String[]{fmt(nps.promoteurs()), fmt(nps.passifs()), fmt(nps.detracteurs()), fmt(nps.totalReponses()), fmt(nps.nps())}));

            List<String[]> mensuelRows = mensuel().stream()
                    .map(m -> new String[]{m.mois(), fmt(m.satisfactionMoyenne()), fmt(m.nps()), fmt(m.reponses())})
                    .toList();
            addPdfSection(document, "Tendance mensuelle (12 derniers mois)", sectionFont, normalFont,
                    new String[]{"Mois", "Satisfaction moyenne", "NPS", "Reponses"}, mensuelRows);

            List<String[]> notesRows = repartitionNotes().parNote().entrySet().stream()
                    .map(e -> new String[]{String.valueOf(e.getKey()), fmt(e.getValue())})
                    .toList();
            addPdfSection(document, "Repartition des notes", sectionFont, normalFont, new String[]{"Note", "Nombre"}, notesRows);

            addPdfDimensionSection(document, "Satisfaction par produit", sectionFont, normalFont, parProduit());
            addPdfDimensionSection(document, "Satisfaction par gestionnaire", sectionFont, normalFont, parGestionnaire());
            addPdfDimensionSection(document, "Satisfaction par courtier", sectionFont, normalFont, parCourtier());

            ReclamationStatistiquesResponse reclamations = reclamationService.statistiques();
            addPdfSection(document, "Reclamations", sectionFont, normalFont,
                    new String[]{"Total", "Ouvertes", "Resolues", "Fermees", "Creees auto.", "Delai moyen (h)"},
                    List.<String[]>of(new String[]{fmt(reclamations.total()), fmt(reclamations.ouvertes()), fmt(reclamations.resolues()),
                            fmt(reclamations.fermees()), fmt(reclamations.creeesAutomatiquement()), fmt(reclamations.delaiMoyenResolutionHeures())}));

            List<String[]> parStatutRows = reclamations.parStatut().entrySet().stream()
                    .map(e -> new String[]{String.valueOf(e.getKey()), fmt(e.getValue())})
                    .toList();
            addPdfSection(document, "Reclamations par statut", sectionFont, normalFont, new String[]{"Statut", "Nombre"}, parStatutRows);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Echec de la generation du PDF du dashboard", e);
        }
    }

    private void addPdfDimensionSection(Document document, String titre, Font sectionFont, Font normalFont,
                                         List<SatisfactionParDimensionItem> items) throws DocumentException {
        List<String[]> rows = items.stream()
                .map(item -> new String[]{item.nom(), fmt(item.satisfactionMoyenne()), fmt(item.nps()), fmt(item.reponses())})
                .toList();
        addPdfSection(document, titre, sectionFont, normalFont, new String[]{"Nom", "Satisfaction moyenne", "NPS", "Reponses"}, rows);
    }

    private void addPdfSection(Document document, String titre, Font sectionFont, Font normalFont,
                                String[] headers, List<String[]> rows) throws DocumentException {
        document.add(new Paragraph(titre, sectionFont));

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(14);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            cell.setGrayFill(0.9f);
            table.addCell(cell);
        }
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Paragraph("Aucune donnee", normalFont));
            empty.setColspan(headers.length);
            table.addCell(empty);
        } else {
            for (String[] row : rows) {
                for (String value : row) {
                    table.addCell(new Paragraph(value, normalFont));
                }
            }
        }
        document.add(table);
    }

    private String formatDateHeure(Instant instant) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC).format(instant) + " UTC";
    }

    private String fmt(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Double d) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        return String.valueOf(value);
    }

    private <T> List<SatisfactionParDimensionItem> parDimension(Function<EnqueteSatisfaction, T> extracteur,
                                                                  Function<T, Long> idExtractor,
                                                                  Function<T, String> nomExtractor) {
        List<EnqueteSatisfaction> repondues = repondues(enqueteSatisfactionRepository.findAll());

        Map<T, List<EnqueteSatisfaction>> parGroupe = repondues.stream()
                .filter(e -> extracteur.apply(e) != null)
                .collect(Collectors.groupingBy(extracteur));

        return parGroupe.entrySet().stream()
                .map(entry -> new SatisfactionParDimensionItem(
                        idExtractor.apply(entry.getKey()),
                        nomExtractor.apply(entry.getKey()),
                        satisfactionMoyenne(entry.getValue()),
                        calculerNps(entry.getValue()),
                        entry.getValue().size()))
                .sorted((a, b) -> Long.compare(b.reponses(), a.reponses()))
                .toList();
    }

    private List<EnqueteSatisfaction> repondues(List<EnqueteSatisfaction> enquetes) {
        return enquetes.stream()
                .filter(e -> e.getStatut() == EnqueteStatut.REPONDUE && e.getScoreNps() != null && e.getDateReponse() != null)
                .toList();
    }

    private Double satisfactionMoyenne(List<EnqueteSatisfaction> repondues) {
        return repondues.isEmpty() ? null : repondues.stream().mapToInt(EnqueteSatisfaction::getScoreNps).average().orElse(0);
    }

    private Integer calculerNps(List<EnqueteSatisfaction> repondues) {
        if (repondues.isEmpty()) {
            return null;
        }
        long promoteurs = repondues.stream().filter(e -> e.getScoreNps() >= 9).count();
        long detracteurs = repondues.stream().filter(e -> e.getScoreNps() <= 6).count();
        return (int) Math.round((promoteurs - detracteurs) * 100.0 / repondues.size());
    }

    private void requireAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.canViewAll(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux profils ADMIN/MARKETING/MANAGER");
        }
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElse(user.getUsername());
    }

    private String displayNamePartenaire(Partenaire partenaire) {
        if (partenaire == null) {
            return null;
        }
        String prenoms = partenaire.getPrenoms();
        return prenoms == null || prenoms.isBlank() ? partenaire.getNom() : partenaire.getNom() + " " + prenoms;
    }
}
