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
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Contenu d'un devis : corps de l'e-mail (HTML + texte) et PDF joint (2026-11-03).
 *
 * GABARIT FIGE : e-mail et PDF ont ete valides par l'utilisateur (maquettes). Aucune modification de structure, de textes ou de
 * couleurs sans nouvelle maquette validee. Le test QuoteDocumentServiceTest verrouille les elements essentiels.
 */
@Service
@RequiredArgsConstructor
public class QuoteDocumentService {

    public static final String TEXTE_LEGAL_DEFAUT = "Ce document est une proposition d'assurance établie à partir des informations que vous avez déclarées. "
            + "Il ne constitue pas un contrat d'assurance : la couverture ne prend effet qu'après acceptation du devis, paiement de la prime "
            + "et émission de l'attestation. Les garanties sont soumises aux conditions générales et aux exclusions du contrat.";
    public static final String PIED_DEFAUT = "Une offre AssurZen Côte d'Ivoire\nBesoin d'aide ? Répondez à cet e-mail ou appelez le service client.";
    public static final String MENTION_PDF = "Le devis détaillé est joint à cet e-mail (PDF).";
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
        return QuoteService.VTC.equals(q.productKey()) ? "Assurance automobile VTC" : "Assurance moto";
    }

    public static String montant(long v) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(v).replace('\u202f', ' ').replace('\u00a0', ' ') + " F CFA";
    }

    /** Montant court de l'e-mail (maquette) : « 185 667 F ». */
    static String montantCourt(long v) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(v).replace('\u202f', ' ').replace('\u00a0', ' ') + " F";
    }

    /** Echappe uniquement les caracteres HTML sensibles (les accents restent tels quels, la page est en UTF-8). */
    private static String h(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
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

    /** Vehicule tel que presente dans l'e-mail (maquette) : « Toyota Corolla · 5 CV ». */
    private static String vehiculeCourt(QuoteView q) {
        StringBuilder sb = new StringBuilder();
        sb.append(q.brand() == null ? "" : q.brand()).append(' ').append(q.model() == null ? "" : q.model());
        if (q.fiscalPower() != null) {
            sb.append(" · ").append(q.fiscalPower()).append(" CV");
        } else if (q.cylinderCc() != null) {
            sb.append(" · ").append(q.cylinderCc()).append(" cm3");
        }
        return sb.toString().trim();
    }

    public static final String MENTION_EMAIL_DEFAUT = "Ce devis est une proposition et ne constitue pas un contrat. Il devient un contrat après acceptation et paiement.";
    private static final String FONT = "system-ui,-apple-system,'Segoe UI',Arial,sans-serif";
    private static final String TD = "padding:8px 0;border-bottom:1px solid #e2e7f2;";

    /** Nom affiche dans l'en-tete de l'e-mail : cle QUOTE_EMAIL_BRAND. */
    public String marque() {
        return configuration.getString("QUOTE_EMAIL_BRAND", MARQUE_DEFAUT);
    }

    /** Slogan officiel : cle QUOTE_EMAIL_SLOGAN. */
    public String slogan() {
        return configuration.getString("QUOTE_EMAIL_SLOGAN", SLOGAN_DEFAUT);
    }

    private String mentionEmail() {
        return configuration.getString("QUOTE_EMAIL_MENTION", MENTION_EMAIL_DEFAUT);
    }

    /**
     * Corps HTML (maquette validee, vue B) ; styles en ligne pour les clients de messagerie.
     * message = mot personnel facultatif (echappe), expediteur = nom affiche dans la signature.
     */
    public String html(QuoteView q, String message, String expediteur, boolean avecPdf) {
        var p = q.premium();
        long frais = p.taxes() + p.accessories() + p.cedeao() + p.attestation() + p.fga();
        List<String> libelles = new ArrayList<>();
        if (p.taxes() > 0) libelles.add("taxes");
        if (p.accessories() > 0) libelles.add("accessoires");
        if (p.cedeao() > 0) libelles.add("CEDEAO");
        if (p.attestation() > 0) libelles.add("attestation");
        if (p.fga() > 0) libelles.add("FGA");
        String libelleFrais = libelles.isEmpty() ? "" : Character.toUpperCase(libelles.get(0).charAt(0)) + String.join(", ", libelles).substring(1);

        StringBuilder lignes = new StringBuilder();
        ligne(lignes, "Véhicule", vehiculeCourt(q), "");
        ligne(lignes, "Période de garantie", q.effectiveDate().format(DATE) + " → " + q.expiryDate().format(DATE), "");
        ligne(lignes, "Prime nette", montantCourt(p.netPremium()), "");
        if (p.discount() > 0) {
            String pct = q.discountPercent() == null ? "" : " " + q.discountPercent().stripTrailingZeros().toPlainString() + " %";
            ligne(lignes, "Remise" + pct, "- " + montantCourt(p.discount()), "color:#16a34a;");
        }
        if (frais > 0) {
            ligne(lignes, libelleFrais, montantCourt(frais), "");
        }
        lignes.append("<tr><td style=\"padding:12px 0 8px;font-size:16px;font-weight:800;color:#1b3b8f\">Total à payer</td>")
                .append("<td style=\"padding:12px 0 8px;text-align:right;font-size:16px;font-weight:800;color:#1b3b8f\">")
                .append(montantCourt(p.total())).append("</td></tr>");

        StringBuilder pastilles = new StringBuilder();
        for (GuaranteeLine g : q.guarantees()) {
            pastilles.append("<span style=\"display:inline-block;background:#e8f7ee;color:#16a34a;border-radius:99px;padding:5px 12px;margin:0 8px 8px 0;font-size:12px;font-weight:600\">✓ ")
                    .append(h(g.label())).append("</span>");
        }

        String mot = "";
        if (message != null && !message.isBlank()) {
            mot = "<div style=\"background:#f7f8fb;border-left:3px solid #1b3b8f;padding:10px 14px;border-radius:6px;margin:0 0 14px;color:#4a5165\">"
                    + "<div style=\"white-space:pre-line\">" + h(message.trim()) + "</div>"
                    + "<div style=\"margin-top:6px;font-size:12px;color:#8a90a2\">— " + h(expediteur) + ", conseiller AssurZen</div></div>";
        }
        String pied = h(pied()).replace("\n", "<br>") + (avecPdf ? "<br>" + h(MENTION_PDF) : "");

        return "<div style=\"margin:0;padding:20px;background:#e9ebf2;font-family:" + FONT + "\">"
                + "<div style=\"max-width:620px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;color:#1e2a4c;box-shadow:0 8px 30px rgba(30,35,60,.12)\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#1b3b8f\" "
                + "style=\"background-color:#1b3b8f;background-image:linear-gradient(135deg,#1b3b8f,#14265c);color:#ffffff\"><tr><td style=\"padding:26px 30px;color:#ffffff\">"
                + "<div style=\"font-weight:800;font-size:22px;letter-spacing:-.02em;color:#ffffff\">" + h(marque()) + "</div>"
                + "<div style=\"margin-top:6px;opacity:.9;color:#ffffff\">" + h(slogan()) + "</div></td></tr></table>"
                + "<div style=\"padding:26px 30px\">"
                + "<h3 style=\"margin:0 0 8px;font-size:18px;color:#1e2a4c\">Bonjour " + h(q.prospectName()) + ",</h3>"
                + "<p style=\"margin:0 0 14px;color:#4a5165\">Merci de votre confiance. Voici le devis que nous avons préparé pour votre véhicule.</p>"
                + mot
                + "<div style=\"background:#eef2fc;border:1px solid #c9d5f3;border-radius:14px;padding:18px;text-align:center;margin:18px 0\">"
                + "<div style=\"font-size:13px;color:#6b7385\">" + h(produit(q)) + " · " + h(q.formulaName()) + " · " + q.durationMonths() + " mois</div>"
                + "<div style=\"font-size:34px;font-weight:800;color:#1b3b8f;letter-spacing:-.02em\">" + montantCourt(p.total()) + "</div>"
                + "<div style=\"font-size:13px;color:#6b7385\">Valable jusqu'au " + q.validUntil().format(DATE) + "</div></div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;border-collapse:collapse;font-size:14px\">" + lignes + "</table>"
                + "<div style=\"margin:14px 0\">" + pastilles + "</div>"
                + "<p style=\"font-size:12px;color:#8a90a2;text-align:center;margin:0\">" + h(mentionEmail()) + "</p></div>"
                + "<div style=\"background:#f7f8fb;border-top:3px solid #ec7c1e;padding:18px 30px;color:#8a90a2;font-size:12px;text-align:center;line-height:1.6\">" + pied + "</div>"
                + "</div></div>";
    }

    private static void ligne(StringBuilder sb, String libelle, String valeur, String styleValeur) {
        sb.append("<tr><td style=\"").append(TD).append("\">").append(h(libelle)).append("</td><td style=\"").append(TD)
                .append("text-align:right;font-weight:600;").append(styleValeur).append("\">").append(h(valeur)).append("</td></tr>");
    }

    // ------------------------------------------------------------------ PDF (maquette validee, 2026-11-03)

    // Palette AssurZen commune a l'e-mail et au PDF (maquette validee).
    public static final String MARQUE_DEFAUT = "AssurZen Assurance";
    public static final String SLOGAN_DEFAUT = "Votre sérénité, notre priorité.";
    static final String BLEU = "#1b3b8f";
    static final String MARINE = "#14265c";
    static final String ORANGE_HEX = "#ec7c1e";
    static final String SOFT_HEX = "#eef2fc";
    static final String SOFTB_HEX = "#c9d5f3";
    static final String TEXTE_HEX = "#1e2a4c";

    private static final float PW = PageSize.A4.getWidth();
    private static final float PH = PageSize.A4.getHeight();
    private static final float MG = 33f;
    private static final float HDR = 80f;
    private static final float FOOT = 54f;
    private static final java.awt.Color VIOLET = java.awt.Color.decode(BLEU);
    private static final java.awt.Color VIOLET2 = java.awt.Color.decode(MARINE);
    private static final java.awt.Color GRIS = new java.awt.Color(0x6b, 0x73, 0x85);
    private static final java.awt.Color TEXTE = java.awt.Color.decode(TEXTE_HEX);
    private static final java.awt.Color ORANGE = java.awt.Color.decode(ORANGE_HEX);
    private static final java.awt.Color BORD = java.awt.Color.decode("#e2e7f2");
    private static final java.awt.Color LIGNE = new java.awt.Color(0xee, 0xf0, 0xf6);
    private static final java.awt.Color VERT = new java.awt.Color(0x16, 0xa3, 0x4a);
    private static final java.awt.Color FOND = new java.awt.Color(0xf7, 0xf8, 0xfb);
    private static final java.awt.Color SOFT = java.awt.Color.decode(SOFT_HEX);
    private static final java.awt.Color SOFTB = java.awt.Color.decode(SOFTB_HEX);
    private static final java.awt.Color PIEDC = new java.awt.Color(0x8a, 0x90, 0xa2);
    private static final java.awt.Color BLANC = java.awt.Color.WHITE;
    private static final java.awt.Color C_NET = VIOLET;
    private static final java.awt.Color C_TAXES = java.awt.Color.decode("#ec7c1e");
    private static final java.awt.Color C_ACC = java.awt.Color.decode("#4a69bd");
    private static final java.awt.Color C_CEDEAO = java.awt.Color.decode("#f2a45e");
    private static final java.awt.Color C_ATTEST = java.awt.Color.decode("#8aa0d8");
    private static final java.awt.Color C_FGA = java.awt.Color.decode("#14265c");

    /** PDF du devis (compatibilite) : sans nom d'expediteur. */
    public byte[] pdf(QuoteView q) {
        return pdf(q, null);
    }

    /** PDF du devis (A4, maquette validee) : prix fige du devis, remise comprise. expediteur = nom affiche dans « Etabli par ». */
    public byte[] pdf(QuoteView q, String expediteur) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            new PdfDevis(doc, writer, q, expediteur).composer();
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Generation du PDF impossible", e);
        }
    }

    private static String statut(String s) {
        return switch (s == null ? "" : s) {
            case "ACCEPTE" -> "Accepté";
            case "EXPIRE" -> "Expiré";
            default -> "Calculé";
        };
    }

    private static String energie(String e) {
        return switch (e == null ? "" : e) {
            case "ESSENCE" -> "Essence";
            case "DIESEL" -> "Diesel";
            case "HYBRIDE" -> "Hybride";
            case "ELECTRIQUE" -> "Électrique";
            default -> e == null ? "" : e;
        };
    }

    private static String tiret(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private final class PdfDevis {
        private final Document doc;
        private final PdfWriter writer;
        private final QuoteView q;
        private final String expediteur;
        private final com.lowagie.text.pdf.BaseFont reg;
        private final com.lowagie.text.pdf.BaseFont bold;
        private com.lowagie.text.pdf.PdfContentByte cb;
        private com.lowagie.text.pdf.PdfTemplate total;
        private int page = 0;
        private float top;

        PdfDevis(Document doc, PdfWriter writer, QuoteView q, String expediteur) throws Exception {
            this.doc = doc;
            this.writer = writer;
            this.q = q;
            this.expediteur = expediteur;
            this.reg = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.WINANSI, false);
            this.bold = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA_BOLD, com.lowagie.text.pdf.BaseFont.WINANSI, false);
        }

        // ---- primitives
        private void txt(String s, com.lowagie.text.pdf.BaseFont f, float size, java.awt.Color c, float x, float baseline, int align) {
            cb.beginText();
            cb.setFontAndSize(f, size);
            cb.setColorFill(c);
            cb.showTextAligned(align, s, x, PH - baseline, 0);
            cb.endText();
        }

        private void petitTitre(String s, float x, float baseline) {
            cb.beginText();
            cb.setFontAndSize(bold, 7.4f);
            cb.setColorFill(GRIS);
            cb.setCharacterSpacing(0.6f);
            cb.showTextAligned(Element.ALIGN_LEFT, s.toUpperCase(Locale.FRANCE), x, PH - baseline, 0);
            cb.setCharacterSpacing(0);
            cb.endText();
        }

        private void rect(float x, float topY, float w, float h, float r, java.awt.Color fill, java.awt.Color stroke) {
            cb.saveState();
            cb.roundRectangle(x, PH - topY - h, w, h, r);
            if (fill != null && stroke != null) {
                cb.setColorFill(fill);
                cb.setColorStroke(stroke);
                cb.setLineWidth(0.75f);
                cb.fillStroke();
            } else if (fill != null) {
                cb.setColorFill(fill);
                cb.fill();
            } else {
                cb.setColorStroke(stroke);
                cb.setLineWidth(0.75f);
                cb.stroke();
            }
            cb.restoreState();
        }

        private void trait(float x1, float x2, float topY, java.awt.Color c, float epaisseur) {
            cb.saveState();
            cb.setColorStroke(c);
            cb.setLineWidth(epaisseur);
            cb.moveTo(x1, PH - topY);
            cb.lineTo(x2, PH - topY);
            cb.stroke();
            cb.restoreState();
        }

        private float larg(String s, com.lowagie.text.pdf.BaseFont f, float size) {
            return f.getWidthPoint(s, size);
        }

        private String tronquer(String s, com.lowagie.text.pdf.BaseFont f, float size, float max) {
            if (larg(s, f, size) <= max) {
                return s;
            }
            String t = s;
            while (t.length() > 1 && larg(t + "…", f, size) > max) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "…";
        }

        private List<String> couper(String s, com.lowagie.text.pdf.BaseFont f, float size, float max) {
            List<String> lignes = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String mot : s.split(" ")) {
                String essai = cur.length() == 0 ? mot : cur + " " + mot;
                if (larg(essai, f, size) > max && cur.length() > 0) {
                    lignes.add(cur.toString());
                    cur = new StringBuilder(mot);
                } else {
                    cur = new StringBuilder(essai);
                }
            }
            if (cur.length() > 0) {
                lignes.add(cur.toString());
            }
            return lignes;
        }

        // ---- pages
        private void nouvellePage() {
            if (page > 0) {
                doc.newPage();
            }
            page++;
            cb = writer.getDirectContent();
            if (total == null) {
                total = cb.createTemplate(24, 10);
            }
            filigrane();
            if (page == 1) {
                entete();
                top = HDR + 20;
            } else {
                top = MG;
            }
        }

        private void filigrane() {
            cb.saveState();
            com.lowagie.text.pdf.PdfGState gs = new com.lowagie.text.pdf.PdfGState();
            gs.setFillOpacity(0.05f);
            cb.setGState(gs);
            cb.beginText();
            cb.setFontAndSize(bold, 92);
            cb.setColorFill(VIOLET);
            cb.showTextAligned(Element.ALIGN_CENTER, "AssurZen", PW - 6, PH * 0.47f, 90);
            cb.endText();
            cb.restoreState();
        }

        private void entete() {
            cb.saveState();
            try {
                com.lowagie.text.pdf.PdfShading sh = com.lowagie.text.pdf.PdfShading.simpleAxial(writer, 0, PH, PW, PH - HDR, VIOLET, VIOLET2);
                cb.setShadingFill(new com.lowagie.text.pdf.PdfShadingPattern(sh));
            } catch (Exception e) {
                cb.setColorFill(VIOLET);
            }
            cb.rectangle(0, PH - HDR, PW, HDR);
            cb.fill();
            cb.restoreState();
            txt(marque(), bold, 19.5f, BLANC, MG, 38, Element.ALIGN_LEFT);
            txt(slogan(), reg, 9.75f, new java.awt.Color(0xee, 0xed, 0xfc), MG, 55, Element.ALIGN_LEFT);
            float dr = PW - MG;
            cb.beginText();
            cb.setFontAndSize(reg, 8.25f);
            cb.setColorFill(new java.awt.Color(0xe3, 0xe1, 0xfa));
            cb.setCharacterSpacing(0.6f);
            cb.showTextAligned(Element.ALIGN_RIGHT, "DEVIS N°", dr, PH - 25, 0);
            cb.setCharacterSpacing(0);
            cb.endText();
            txt(q.reference(), bold, 14.25f, BLANC, dr, 42, Element.ALIGN_RIGHT);
            String pastille = statut(q.status()) + " · valable jusqu'au " + q.validUntil().format(DATE);
            float pw = larg(pastille, bold, 8.25f) + 18;
            cb.saveState();
            com.lowagie.text.pdf.PdfGState gs = new com.lowagie.text.pdf.PdfGState();
            gs.setFillOpacity(0.22f);
            cb.setGState(gs);
            cb.setColorFill(BLANC);
            cb.roundRectangle(dr - pw, PH - 66, pw, 15, 7.5f);
            cb.fill();
            cb.restoreState();
            txt(pastille, bold, 8.25f, BLANC, dr - pw / 2, 61.5f, Element.ALIGN_CENTER);
        }

        private void piedDePage() {
            float y0 = PH - FOOT;
            cb.saveState();
            cb.setColorFill(FOND);
            cb.rectangle(0, 0, PW, FOOT);
            cb.fill();
            cb.restoreState();
            trait(0, PW, y0, ORANGE, 2.25f);
            String[] lignes = pied().split("\\r?\\n");
            float b = y0 + 17;
            for (int i = 0; i < lignes.length; i++) {
                if (lignes[i].isBlank()) {
                    continue;
                }
                txt(lignes[i], i == 0 ? bold : reg, 8.25f, i == 0 ? TEXTE : PIEDC, PW / 2, b, Element.ALIGN_CENTER);
                b += 11.5f;
            }
            txt("Devis " + q.reference() + " · valable jusqu'au " + q.validUntil().format(DATE), reg, 8.25f, PIEDC, PW / 2, b, Element.ALIGN_CENTER);
            String debut = "Page " + page + " / ";
            float x = PW - MG - larg(debut, reg, 7.5f) - larg("9", reg, 7.5f) * 1.2f;
            txt(debut, reg, 7.5f, new java.awt.Color(0xa3, 0xa9, 0xb8), x, PH - 5, Element.ALIGN_LEFT);
            cb.addTemplate(total, x + larg(debut, reg, 7.5f), 5 - 2.2f);
        }

        private void besoin(float hauteur) {
            if (top + hauteur > PH - FOOT - 8) {
                piedDePage();
                nouvellePage();
            }
        }

        // ---- contenu
        void composer() {
            nouvellePage();
            boolean vtc = QuoteService.VTC.equals(q.productKey());
            txt(produit(q), bold, 15, TEXTE, MG, top + 13, Element.ALIGN_LEFT);
            String cree = q.createdAt() == null ? "" : " · établi le " + q.createdAt().atZone(java.time.ZoneId.of("Africa/Abidjan")).toLocalDate().format(DATE);
            txt(q.formulaName() + cree, reg, 9.75f, GRIS, MG, top + 28, Element.ALIGN_LEFT);
            top += 40;
            cadres(vtc);
            blocPrix();
            detailPrix(vtc);
            garanties();
            mentionLegale();
            signatures();
            piedDePage();
            total.beginText();
            total.setFontAndSize(reg, 7.5f);
            total.setColorFill(new java.awt.Color(0xa3, 0xa9, 0xb8));
            total.setTextMatrix(0, 2.2f);
            total.showText(String.valueOf(page));
            total.endText();
        }

        private void cadres(boolean vtc) {
            List<String[]> prospect = new ArrayList<>();
            prospect.add(new String[]{"Nom", tiret(q.prospectName())});
            prospect.add(new String[]{"Téléphone", tiret(q.prospectPhone())});
            if (q.prospectEmail() != null && !q.prospectEmail().isBlank()) {
                prospect.add(new String[]{"E-mail", q.prospectEmail()});
            }
            List<String[]> veh = new ArrayList<>();
            String immat = q.registrationNumber();
            if (vtc) {
                veh.add(new String[]{"Véhicule", ((q.brand() == null ? "" : q.brand()) + " " + (q.model() == null ? "" : q.model())).trim()});
                veh.add(new String[]{"Puissance fiscale", q.fiscalPower() == null ? "—" : q.fiscalPower() + " CV · " + energie(q.energy())});
                veh.add(new String[]{"Places", q.seats() == null ? "—" : String.valueOf(q.seats())});
                veh.add(new String[]{"Mise en circulation", q.firstRegistrationYear() == null ? "—" : String.valueOf(q.firstRegistrationYear())});
                veh.add(immat != null && !immat.isBlank() ? new String[]{"Immatriculation", immat} : new String[]{"N° de châssis", tiret(q.chassisNumber())});
                boolean decl = Boolean.TRUE.equals(q.vtcDeclared());
                String usage = (decl ? "Déclaré" : "Non déclaré") + (q.platform() == null || q.platform().isBlank() ? "" : " · " + q.platform());
                veh.add(new String[]{"Usage VTC", usage});
            } else {
                String type = "TRICYCLE".equals(q.vehicleType()) ? "Tricycle" : "Moto";
                veh.add(new String[]{"Véhicule", type + " · " + tiret(q.brand())});
                veh.add(new String[]{"Cylindrée", q.cylinderCc() == null ? "—" : q.cylinderCc() + " cc"});
                veh.add(new String[]{"Immatriculation", tiret(immat)});
                veh.add(new String[]{"N° de châssis", tiret(q.chassisNumber())});
            }
            float lh = 14f;
            int n = Math.max(prospect.size(), veh.size());
            float h = 9 + 9 + n * lh + 4;
            besoin(h + 10);
            float largeur = (PW - 2 * MG - 12) / 2;
            boite("Prospect", prospect, MG, largeur, h, lh);
            boite("Véhicule", veh, MG + largeur + 12, largeur, h, lh);
            top += h + 11;
        }

        private void boite(String titre, List<String[]> lignes, float x, float w, float h, float lh) {
            rect(x, top, w, h, 9, null, BORD);
            petitTitre(titre, x + 12, top + 15);
            float b = top + 15 + 13;
            for (String[] l : lignes) {
                txt(l[0], reg, 9, GRIS, x + 12, b, Element.ALIGN_LEFT);
                float max = w - 24 - larg(l[0], reg, 9) - 8;
                txt(tronquer(l[1], bold, 9, max), bold, 9, TEXTE, x + w - 12, b, Element.ALIGN_RIGHT);
                b += lh;
            }
        }

        private void blocPrix() {
            var p = q.premium();
            float h = 62;
            besoin(h + 30);
            float w = PW - 2 * MG;
            rect(MG, top, w, h, 10, SOFT, SOFTB);
            txt("Total à payer", reg, 9, GRIS, MG + 16, top + 19, Element.ALIGN_LEFT);
            txt(montantCourt(p.total()), bold, 28.5f, VIOLET, MG + 16, top + 49, Element.ALIGN_LEFT);
            float dr = MG + w - 16;
            txt("Période de garantie", reg, 9, GRIS, dr, top + 19, Element.ALIGN_RIGHT);
            periode(dr, top + 35);
            txt(q.durationMonths() + " mois", reg, 9, GRIS, dr, top + 49, Element.ALIGN_RIGHT);
            top += h + 5;
            // barre de repartition
            long[] val = {p.netPremium(), p.taxes(), p.accessories(), p.cedeao(), p.attestation(), p.fga()};
            java.awt.Color[] col = {C_NET, C_TAXES, C_ACC, C_CEDEAO, C_ATTEST, C_FGA};
            long s = 0;
            for (long v : val) {
                if (v > 0) s += v;
            }
            if (s > 0) {
                cb.saveState();
                cb.roundRectangle(MG, PH - top - 6.75f, w, 6.75f, 3.4f);
                cb.clip();
                cb.newPath();
                float x = MG;
                for (int i = 0; i < val.length; i++) {
                    if (val[i] <= 0) continue;
                    float lg = w * val[i] / s;
                    cb.setColorFill(col[i]);
                    cb.rectangle(x, PH - top - 6.75f, lg + 0.4f, 6.75f);
                    cb.fill();
                    x += lg;
                }
                cb.restoreState();
            }
            top += 6.75f + 12;
        }

        /** Periode « jj/mm/aaaa -> jj/mm/aaaa » alignee a droite (la fleche est dessinee : absente de WinAnsi). */
        private void periode(float droite, float baseline) {
            String a = q.effectiveDate().format(DATE);
            String b = q.expiryDate().format(DATE);
            float fa = larg(a, bold, 11.25f);
            float fb = larg(b, bold, 11.25f);
            float fleche = 14f;
            float x = droite - fb;
            txt(b, bold, 11.25f, TEXTE, droite, baseline, Element.ALIGN_RIGHT);
            float x2 = x - 5;
            float x1 = x2 - fleche;
            float ym = PH - baseline + 3.2f;
            cb.saveState();
            cb.setColorStroke(TEXTE);
            cb.setLineWidth(1f);
            cb.moveTo(x1, ym);
            cb.lineTo(x2, ym);
            cb.moveTo(x2 - 3.4f, ym + 2.8f);
            cb.lineTo(x2, ym);
            cb.lineTo(x2 - 3.4f, ym - 2.8f);
            cb.stroke();
            cb.restoreState();
            txt(a, bold, 11.25f, TEXTE, x1 - 5, baseline, Element.ALIGN_RIGHT);
        }

        private void detailPrix(boolean vtc) {
            var p = q.premium();
            record Ligne(String libelle, long montant, java.awt.Color couleur, boolean remise) {
            }
            List<Ligne> lignes = new ArrayList<>();
            lignes.add(new Ligne("Prime nette", p.netPremium(), C_NET, false));
            if (p.discount() > 0) {
                String pct = q.discountPercent() == null ? "" : " " + q.discountPercent().stripTrailingZeros().toPlainString() + " %";
                lignes.add(new Ligne("Remise" + pct, -p.discount(), null, true));
            }
            lignes.add(new Ligne("Taxes", p.taxes(), C_TAXES, false));
            lignes.add(new Ligne("Accessoires", p.accessories(), C_ACC, false));
            if (vtc) {
                lignes.add(new Ligne("CEDEAO", p.cedeao(), C_CEDEAO, false));
                lignes.add(new Ligne("Attestation", p.attestation(), C_ATTEST, false));
            }
            lignes.add(new Ligne("FGA", p.fga(), C_FGA, false));
            float rh = 20;
            float h = 14 + lignes.size() * rh + 28;
            besoin(h + 10);
            petitTitre("Détail du prix", MG, top + 7);
            cb.beginText();
            cb.setFontAndSize(bold, 7.4f);
            cb.setColorFill(GRIS);
            cb.setCharacterSpacing(0.6f);
            cb.showTextAligned(Element.ALIGN_RIGHT, "MONTANT", PW - MG, PH - (top + 7), 0);
            cb.setCharacterSpacing(0);
            cb.endText();
            top += 14;
            for (Ligne l : lignes) {
                float b = top + 13.5f;
                float xl = MG;
                if (l.couleur() != null) {
                    cb.saveState();
                    cb.setColorFill(l.couleur());
                    cb.circle(MG + 3, PH - (b - 3.2f), 3);
                    cb.fill();
                    cb.restoreState();
                    xl = MG + 14;
                }
                java.awt.Color c = l.remise() ? VERT : TEXTE;
                txt(l.libelle(), reg, 9.75f, c, xl, b, Element.ALIGN_LEFT);
                String m = l.montant() < 0 ? "– " + montantCourt(-l.montant()) : montantCourt(l.montant());
                txt(m, bold, 9.75f, c, PW - MG, b, Element.ALIGN_RIGHT);
                top += rh;
                trait(MG, PW - MG, top, LIGNE, 0.75f);
            }
            txt("Total à payer", bold, 11.25f, VIOLET, MG, top + 20, Element.ALIGN_LEFT);
            txt(montantCourt(p.total()), bold, 11.25f, VIOLET, PW - MG, top + 20, Element.ALIGN_RIGHT);
            top += 28 + 10;
        }

        private void garanties() {
            List<GuaranteeLine> gs = q.guarantees();
            if (gs == null || gs.isEmpty()) {
                return;
            }
            float h = 0;
            for (GuaranteeLine g : gs) {
                h += 15 + 11.5f + (g.details().isEmpty() ? 0 : 3.5f + g.details().size() * 11.5f);
            }
            besoin(h + 24);
            petitTitre("Garanties incluses", MG, top + 7);
            top += 13;
            float w = PW - 2 * MG;
            rect(MG, top, w, h, 9, null, BORD);
            float y = top;
            for (int i = 0; i < gs.size(); i++) {
                GuaranteeLine g = gs.get(i);
                float b = y + 7.5f + 9.5f;
                cb.saveState();
                cb.setColorStroke(VERT);
                cb.setLineWidth(1.5f);
                cb.moveTo(MG + 12, PH - (b - 3.6f));
                cb.lineTo(MG + 15, PH - (b - 0.4f));
                cb.lineTo(MG + 20, PH - (b - 7f));
                cb.stroke();
                cb.restoreState();
                txt(g.label(), bold, 9.75f, VERT, MG + 26, b, Element.ALIGN_LEFT);
                float bb = b + 3.5f;
                for (var d : g.details()) {
                    bb += 11.5f;
                    txt("•", reg, 9, GRIS, MG + 30, bb, Element.ALIGN_LEFT);
                    txt(tronquer(d.label() + " : " + d.amount(), reg, 9, w - 60), reg, 9, GRIS, MG + 40, bb, Element.ALIGN_LEFT);
                }
                y += 15 + 11.5f + (g.details().isEmpty() ? 0 : 3.5f + g.details().size() * 11.5f);
                if (i < gs.size() - 1) {
                    trait(MG, PW - MG, y, LIGNE, 0.75f);
                }
            }
            top += h + 12;
        }

        private void mentionLegale() {
            float w = PW - 2 * MG - 24;
            List<String> lignes = couper(texteLegal(), reg, 7.9f, w);
            float h = lignes.size() * 11.4f + 18;
            besoin(h + 10);
            rect(MG, top, PW - 2 * MG, h, 7.5f, FOND, null);
            float b = top + 9 + 8;
            for (int i = 0; i < lignes.size(); i++) {
                String l = lignes.get(i);
                cb.beginText();
                cb.setFontAndSize(reg, 7.9f);
                cb.setColorFill(GRIS);
                int espaces = l.length() - l.replace(" ", "").length();
                if (i < lignes.size() - 1 && espaces > 0) {
                    cb.setWordSpacing((w - larg(l, reg, 7.9f)) / espaces);
                }
                cb.showTextAligned(Element.ALIGN_LEFT, l, MG + 12, PH - b, 0);
                cb.setWordSpacing(0);
                cb.endText();
                b += 11.4f;
            }
            top += h + 12;
        }

        private void signatures() {
            float h = 63;
            besoin(h + 4);
            float w = (PW - 2 * MG - 12) / 2;
            for (int i = 0; i < 2; i++) {
                float x = MG + i * (w + 12);
                cb.saveState();
                cb.setColorStroke(BORD);
                cb.setLineWidth(1.1f);
                cb.setLineDash(3.2f, 2.6f);
                cb.roundRectangle(x, PH - top - h, w, h, 9);
                cb.stroke();
                cb.restoreState();
            }
            txt("Établi par", reg, 8.25f, GRIS, MG + 11, top + 17, Element.ALIGN_LEFT);
            if (expediteur != null && !expediteur.isBlank()) {
                List<String> nom = couper(expediteur.trim() + ", conseiller AssurZen", bold, 9.75f, w - 22);
                for (int i = 0; i < Math.min(2, nom.size()); i++) {
                    txt(i == 1 && nom.size() > 2 ? tronquer(nom.get(i), bold, 9.75f, w - 22) : nom.get(i), bold, 9.75f, TEXTE, MG + 11, top + 32 + i * 12, Element.ALIGN_LEFT);
                }
            }
            float x2 = MG + w + 12;
            txt("Bon pour accord du client", reg, 8.25f, GRIS, x2 + 11, top + 17, Element.ALIGN_LEFT);
            txt("(date et signature)", reg, 8.25f, GRIS, x2 + 11, top + 29, Element.ALIGN_LEFT);
            top += h + 6;
        }
    }
}
