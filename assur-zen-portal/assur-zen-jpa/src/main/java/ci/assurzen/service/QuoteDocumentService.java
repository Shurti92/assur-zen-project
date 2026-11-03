package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.GuaranteeLine;
import ci.assurzen.dto.QuoteDtos.QuoteView;
import ci.assurzen.services.DynamicConfigurationService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Contenu d'un devis : corps de l'e-mail (HTML + texte) et PDF joint (2026-11-03). */
@Service
@RequiredArgsConstructor
public class QuoteDocumentService {

    static final String TEXTE_LEGAL_DEFAUT = "Ce document est une proposition d'assurance établie à partir des informations que vous avez déclarées. "
            + "Il ne constitue pas un contrat d'assurance : la couverture ne prend effet qu'après acceptation du devis, paiement de la prime "
            + "et émission de l'attestation. Les garanties sont soumises aux conditions générales et aux exclusions du contrat.";
    static final String PIED_DEFAUT = "Une offre AssurZen Côte d'Ivoire\nBesoin d'aide ? Répondez à cet e-mail ou appelez le service client.";
    static final String MENTION_PDF = "Le devis détaillé est joint à cet e-mail (PDF).";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DynamicConfigurationService configuration;

    public String texteLegal() {
        return configuration.getString("QUOTE_EMAIL_LEGAL_TEXT", TEXTE_LEGAL_DEFAUT);
    }

    /** Adresse d'expedition des devis : cle QUOTE_EMAIL_FROM (info@assurzen.ci par defaut). */
    public String adresseExpedition() {
        return configuration.getString("QUOTE_EMAIL_FROM", "customer@assurzen.ci");
    }

    public String pied() {
        return configuration.getString("QUOTE_EMAIL_FOOTER", PIED_DEFAUT);
    }

    public static String produit(QuoteView q) {
        return QuoteService.VTC.equals(q.productKey()) ? "Assurance Automobile VTC" : "Assurance Moto";
    }

    public static String montant(long v) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(v).replace(' ', ' ').replace(' ', ' ') + " F CFA";
    }

    private static String h(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    public String objet(QuoteView q) {
        return "Votre devis AssurZen " + q.reference() + " · " + produit(q);
    }

    private static String vehicule(QuoteView q) {
        StringBuilder sb = new StringBuilder();
        sb.append(q.brand() == null ? "" : q.brand()).append(' ').append(q.model() == null ? "" : q.model());
        if (q.fiscalPower() != null) {
            sb.append(" · ").append(q.fiscalPower()).append(" CV");
        }
        if (q.cylinderCc() != null) {
            sb.append(" · ").append(q.cylinderCc()).append(" cm3");
        }
        if (q.seats() != null) {
            sb.append(" · ").append(q.seats()).append(" places");
        }
        String id = q.registrationNumber() != null ? q.registrationNumber() : q.chassisNumber();
        if (id != null) {
            sb.append(" · ").append(id);
        }
        return sb.toString().trim();
    }

    /** Corps HTML ; message = mot personnel facultatif (echappe), expediteur = nom affiche en signature. */
    public String html(QuoteView q, String message, String expediteur, boolean avecPdf) {
        var p = q.premium();
        StringBuilder lignes = new StringBuilder();
        ligne(lignes, "Prime nette", montant(p.netPremium()));
        if (p.discount() > 0) {
            ligne(lignes, "Remise" + (q.discountPercent() == null ? "" : " (" + q.discountPercent().stripTrailingZeros().toPlainString() + " %)"), "- " + montant(p.discount()));
        }
        if (p.taxes() > 0) ligne(lignes, "Taxes", montant(p.taxes()));
        if (p.accessories() > 0) ligne(lignes, "Accessoires", montant(p.accessories()));
        if (p.cedeao() > 0) ligne(lignes, "CEDEAO", montant(p.cedeao()));
        if (p.attestation() > 0) ligne(lignes, "Attestation", montant(p.attestation()));
        if (p.fga() > 0) ligne(lignes, "FGA", montant(p.fga()));
        StringBuilder garanties = new StringBuilder();
        for (GuaranteeLine g : q.guarantees()) {
            garanties.append("<li>").append(h(g.label()));
            if (!g.details().isEmpty()) {
                garanties.append("<ul>");
                g.details().forEach(d -> garanties.append("<li>").append(h(d.label())).append(" : ").append(h(d.amount())).append("</li>"));
                garanties.append("</ul>");
            }
            garanties.append("</li>");
        }
        String mot = (message == null || message.isBlank()) ? ""
                : "<p style=\"white-space:pre-line\">" + h(message.trim()) + "</p><p>" + h(expediteur) + "</p>";
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:600px;margin:auto\">"
                + "<div style=\"background:#0b3d91;color:#fff;padding:16px 20px;font-size:20px;font-weight:bold\">AssurZen</div>"
                + "<div style=\"padding:20px\"><p>Bonjour " + h(q.prospectName()) + ",</p>"
                + "<p>Voici votre proposition d'assurance <b>" + h(produit(q)) + "</b> (" + h(q.formulaName()) + ").</p>" + mot
                + "<div style=\"border:2px solid #0b3d91;border-radius:8px;padding:14px;text-align:center;margin:16px 0\">"
                + "<div style=\"font-size:13px\">Prix total</div><div style=\"font-size:28px;font-weight:bold;color:#0b3d91\">" + montant(p.total()) + "</div>"
                + "<div style=\"font-size:13px\">pour " + q.durationMonths() + " mois · valable jusqu'au " + q.validUntil().format(DATE) + "</div></div>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px\">"
                + "<tr><td style=\"padding:6px;border-bottom:1px solid #e5e7eb\">Véhicule</td><td style=\"padding:6px;border-bottom:1px solid #e5e7eb;text-align:right\">" + h(vehicule(q)) + "</td></tr>"
                + "<tr><td style=\"padding:6px;border-bottom:1px solid #e5e7eb\">Période</td><td style=\"padding:6px;border-bottom:1px solid #e5e7eb;text-align:right\">du "
                + q.effectiveDate().format(DATE) + " au " + q.expiryDate().format(DATE) + "</td></tr>"
                + lignes + "</table>"
                + "<p><b>Garanties</b></p><ul>" + garanties + "</ul>"
                + "<p style=\"font-size:12px;color:#6b7280\">" + h(texteLegal()) + "</p></div>"
                + "<div style=\"background:#f3f4f6;padding:10px 20px;font-size:12px;color:#6b7280;text-align:center\">"
                + h(pied()).replace("\n", "<br>") + (avecPdf ? "<br>" + h(MENTION_PDF) : "") + "</div></div>";
    }

    private static void ligne(StringBuilder sb, String libelle, String valeur) {
        sb.append("<tr><td style=\"padding:6px;border-bottom:1px solid #e5e7eb\">").append(h(libelle))
                .append("</td><td style=\"padding:6px;border-bottom:1px solid #e5e7eb;text-align:right\">").append(h(valeur)).append("</td></tr>");
    }

    /** PDF du devis (A4) : prix fige du devis, remise comprise. */
    public byte[] pdf(QuoteView q) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titre = new Font(Font.HELVETICA, 20, Font.BOLD);
            Font gras = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 11);
            Font petit = new Font(Font.HELVETICA, 8);
            doc.add(new Paragraph("AssurZen", titre));
            doc.add(new Paragraph("Devis " + q.reference() + " · " + produit(q) + " (" + q.formulaName() + ")", gras));
            doc.add(new Paragraph("Établi pour : " + (q.prospectName() == null ? "" : q.prospectName())
                    + (q.prospectPhone() == null ? "" : " · " + q.prospectPhone()), normal));
            doc.add(new Paragraph("Valable jusqu'au " + q.validUntil().format(DATE), normal));
            doc.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{55, 45});
            cellule(t, "Véhicule", vehicule(q), normal);
            cellule(t, "Période de garantie", q.effectiveDate().format(DATE) + " au " + q.expiryDate().format(DATE) + " (" + q.durationMonths() + " mois)", normal);
            var p = q.premium();
            cellule(t, "Prime nette", montant(p.netPremium()), normal);
            if (p.discount() > 0) cellule(t, "Remise", "- " + montant(p.discount()), normal);
            if (p.taxes() > 0) cellule(t, "Taxes", montant(p.taxes()), normal);
            if (p.accessories() > 0) cellule(t, "Accessoires", montant(p.accessories()), normal);
            if (p.cedeao() > 0) cellule(t, "CEDEAO", montant(p.cedeao()), normal);
            if (p.attestation() > 0) cellule(t, "Attestation", montant(p.attestation()), normal);
            if (p.fga() > 0) cellule(t, "FGA", montant(p.fga()), normal);
            cellule(t, "TOTAL À PAYER", montant(p.total()), gras);
            doc.add(t);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Garanties", gras));
            for (GuaranteeLine g : q.guarantees()) {
                doc.add(new Paragraph("• " + g.label(), normal));
                g.details().forEach(d -> {
                    Paragraph l = new Paragraph("      - " + d.label() + " : " + d.amount(), normal);
                    doc.add(l);
                });
            }
            doc.add(new Paragraph(" "));
            Paragraph legal = new Paragraph(texteLegal(), petit);
            legal.setAlignment(Element.ALIGN_JUSTIFIED);
            doc.add(legal);
            Paragraph pied = new Paragraph(pied().replace("\r", ""), petit);
            pied.setAlignment(Element.ALIGN_CENTER);
            doc.add(pied);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Generation du PDF impossible", e);
        }
    }

    private static void cellule(PdfPTable t, String a, String b, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase(a, f));
        PdfPCell c2 = new PdfPCell(new Phrase(b, f));
        c1.setPadding(5);
        c2.setPadding(5);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c1);
        t.addCell(c2);
    }
}
