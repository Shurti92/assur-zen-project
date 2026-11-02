package ci.assurzen.services;

import ci.assurzen.dto.CommissionStatementResponse;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.Profile;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Releves mensuels des commissions versees a un partenaire (GET /partenaires/{id}/commission-statements,
 * 2026-10-03). Lecture seule, aucune regle d'argent : agrege les paiements par contrat (CommissionPayment,
 * montant reellement verse = amount - avanceImputee) et les avances payees (demandes de paiement partiel
 * "paid", date = validatedAt), par mois calendaire UTC de versement. Reserve au partenaire lui-meme et
 * aux administrateurs.
 */
@Service
@RequiredArgsConstructor
public class CommissionStatementService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MOIS_LIBELLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final CommissionPaymentRepository paymentRepository;
    private final CommissionPartialPaymentRequestRepository requestRepository;
    private final PartenaireRepository partenaireRepository;
    private final ProfileRepository profileRepository;
    private final AuthService authService;

    /** Mois ayant au moins un versement, du plus recent au plus ancien, sans lignes. */
    @Transactional(readOnly = true)
    public List<CommissionStatementResponse> lister(Long partenaireId) {
        verifierAcces(partenaireId);
        return parMois(partenaireId).entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<CommissionStatementResponse.Ligne>>comparingByKey().reversed())
                .map(e -> releve(e.getKey(), e.getValue(), false))
                .toList();
    }

    /** Releve d'un mois ("2026-09") avec ses lignes ; 404 si aucun versement ce mois-la. */
    @Transactional(readOnly = true)
    public CommissionStatementResponse detail(Long partenaireId, String mois) {
        verifierAcces(partenaireId);
        YearMonth cible = parseMois(mois);
        List<CommissionStatementResponse.Ligne> lignes = parMois(partenaireId).get(cible);
        if (lignes == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun versement en " + mois);
        }
        return releve(cible, lignes, true);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long partenaireId, String mois) {
        CommissionStatementResponse releve = detail(partenaireId, mois);
        Partenaire partenaire = partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        return genererPdf(partenaire, releve);
    }

    private Map<YearMonth, List<CommissionStatementResponse.Ligne>> parMois(Long partenaireId) {
        Map<YearMonth, List<CommissionStatementResponse.Ligne>> parMois = new TreeMap<>();
        for (CommissionPayment p : paymentRepository.findByPartenaireIdOrderByPaidAtAsc(partenaireId)) {
            if (p.getPaidAt() == null) {
                continue;
            }
            long montant = p.getAmount() == null ? 0L : p.getAmount();
            long avance = p.getAvanceImputee() == null ? 0L : p.getAvanceImputee();
            parMois.computeIfAbsent(mois(p.getPaidAt()), k -> new ArrayList<>()).add(new CommissionStatementResponse.Ligne(
                    p.getPaidAt(), "COMMISSION",
                    p.getContract() == null ? null : p.getContract().getId(),
                    p.getContract() == null ? null : p.getContract().getPolicyNumber(),
                    montant, avance, p.montantVerse(), p.getReference()));
        }
        for (CommissionPartialPaymentRequest d : requestRepository.findByPartenaireIdOrderByRequestedAtDesc(partenaireId)) {
            if (d.getStatus() != PartialPaymentRequestStatus.paid || d.getValidatedAt() == null) {
                continue;
            }
            long montant = d.getRequestedAmount() == null ? 0L : d.getRequestedAmount();
            parMois.computeIfAbsent(mois(d.getValidatedAt()), k -> new ArrayList<>()).add(new CommissionStatementResponse.Ligne(
                    d.getValidatedAt(), "AVANCE", null, null, montant, 0L, montant, "Demande #" + d.getId()));
        }
        parMois.values().forEach(lignes -> lignes.sort(Comparator.comparing(CommissionStatementResponse.Ligne::date)));
        return parMois;
    }

    private static CommissionStatementResponse releve(YearMonth mois, List<CommissionStatementResponse.Ligne> lignes, boolean avecLignes) {
        List<CommissionStatementResponse.Ligne> commissions = lignes.stream().filter(l -> "COMMISSION".equals(l.type())).toList();
        long montantCommissions = commissions.stream().mapToLong(CommissionStatementResponse.Ligne::montant).sum();
        long avancesImputees = commissions.stream().mapToLong(CommissionStatementResponse.Ligne::avanceImputee).sum();
        long avancesVersees = lignes.stream().filter(l -> "AVANCE".equals(l.type()))
                .mapToLong(CommissionStatementResponse.Ligne::montant).sum();
        long nbContrats = commissions.stream().map(CommissionStatementResponse.Ligne::contractId).filter(Objects::nonNull).distinct().count();
        Instant dernier = lignes.stream().map(CommissionStatementResponse.Ligne::date).max(Comparator.naturalOrder()).orElse(null);
        String statut = mois.equals(YearMonth.now(ZoneOffset.UTC)) ? "EN_COURS" : "CLOTURE";
        return new CommissionStatementResponse(mois.toString(), nbContrats, montantCommissions, avancesImputees, avancesVersees,
                montantCommissions - avancesImputees + avancesVersees, dernier, statut, avecLignes ? lignes : null);
    }

    private static YearMonth mois(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC));
    }

    private static YearMonth parseMois(String mois) {
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mois invalide (attendu AAAA-MM) : " + mois);
        }
    }

    /** Admin, ou le partenaire connecte lui-meme (relation reelle Partenaire.profile) - meme regle que
     * PartenaireNotificationService. Un commercial interne n'a pas acces aux releves d'un partenaire. */
    private void verifierAcces(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            return;
        }
        boolean estCePartenaire = profileRepository.findByUserId(principal.userId())
                .map(Profile::getId)
                .map(partenaireRepository::findByProfile_Id)
                .map(partenaires -> partenaires.stream().anyMatch(p -> p.getId().equals(partenaireId)))
                .orElse(false);
        if (!estCePartenaire) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    private static byte[] genererPdf(Partenaire partenaire, CommissionStatementResponse releve) {
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        YearMonth mois = YearMonth.parse(releve.mois());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph("AssurZen - Releve de commissions", titre));
            document.add(new Paragraph(capitaliser(mois.format(MOIS_LIBELLE))
                    + (releve.statut().equals("EN_COURS") ? " (mois en cours)" : ""), gras));
            document.add(new Paragraph("Partenaire : " + (partenaire.getPrenoms() == null ? "" : partenaire.getPrenoms() + " ")
                    + partenaire.getNom() + (partenaire.getCodePartenaire() == null ? "" : " (" + partenaire.getCodePartenaire() + ")"), normal));
            document.add(new Paragraph("Edite le " + DATE.format(Instant.now()), normal));
            document.add(new Paragraph(" "));

            PdfPTable lignes = new PdfPTable(new float[]{2f, 2f, 3f, 2.2f, 2.2f, 2.2f});
            lignes.setWidthPercentage(100);
            for (String entete : new String[]{"Date", "Type", "Contrat / reference", "Montant", "Avance imputee", "Verse"}) {
                PdfPCell cell = new PdfPCell(new Paragraph(entete, gras));
                lignes.addCell(cell);
            }
            for (CommissionStatementResponse.Ligne l : releve.lignes()) {
                lignes.addCell(new Paragraph(DATE.format(l.date()), normal));
                lignes.addCell(new Paragraph("AVANCE".equals(l.type()) ? "Avance" : "Commission", normal));
                lignes.addCell(new Paragraph(l.policyNumber() != null ? l.policyNumber() : Objects.toString(l.reference(), ""), normal));
                lignes.addCell(montantCell(l.montant(), normal));
                lignes.addCell(montantCell(l.avanceImputee(), normal));
                lignes.addCell(montantCell(l.montantVerse(), normal));
            }
            document.add(lignes);
            document.add(new Paragraph(" "));

            PdfPTable totaux = new PdfPTable(new float[]{4f, 2f});
            totaux.setWidthPercentage(50);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ajouterTotal(totaux, "Contrats payes", String.valueOf(releve.nbContrats()), normal);
            ajouterTotal(totaux, "Commissions", fcfa(releve.montantCommissions()), normal);
            ajouterTotal(totaux, "Deja versees en avance", "- " + fcfa(releve.avancesImputees()), normal);
            ajouterTotal(totaux, "Avances versees", fcfa(releve.avancesVersees()), normal);
            ajouterTotal(totaux, "Total verse", fcfa(releve.montantVerse()), gras);
            document.add(totaux);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Generation du releve PDF impossible", e);
        }
    }

    private static PdfPCell montantCell(long montant, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(fcfa(montant), font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private static void ajouterTotal(PdfPTable table, String libelle, String valeur, Font font) {
        table.addCell(new Paragraph(libelle, font));
        PdfPCell cell = new PdfPCell(new Paragraph(valeur, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private static String fcfa(long montant) {
        return String.format("%,d", montant).replace(',', ' ') + " FCFA";
    }

    private static String capitaliser(String texte) {
        return texte.isEmpty() ? texte : Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }
}
