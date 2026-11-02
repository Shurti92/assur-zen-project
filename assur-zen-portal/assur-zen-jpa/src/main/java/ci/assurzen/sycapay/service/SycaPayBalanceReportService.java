package ci.assurzen.sycapay.service;

import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.TelegramNotificationService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Solde du compte SycaPay sur Telegram (demande utilisateur du 2026-09-26), vers le chat admin deja
 * utilise par les autres alertes (credential.telegram.chat-id) :
 * - rapport chaque jour ouvre (lundi-samedi) a 8h00 heure d'Abidjan, au demarrage de l'activite ;
 * - alerte quand le solde Wave passe sous le seuil SYCAPAY_BALANCE_ALERT_THRESHOLD (configuration
 *   dynamique, defaut 50 000 FCFA), verifie toutes les heures de 9h a 19h, au plus une alerte par jour ;
 * - envoi immediat a la demande (POST /sycapay/balance/telegram, administrateurs).
 * Lecture directe du solde SycaPay (checkcashoutsolde : merchantId seul, aucun virement), meme si
 * SYCAPAY_ENABLED=false. Coupure sans redeploiement : SYCAPAY_BALANCE_REPORT_ENABLED=false.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SycaPayBalanceReportService {

    static final String CLE_SEUIL = "SYCAPAY_BALANCE_ALERT_THRESHOLD";
    static final String CLE_ACTIVATION = "SYCAPAY_BALANCE_REPORT_ENABLED";
    static final long SEUIL_PAR_DEFAUT = 50_000L;
    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final SycaPayClient sycaPayClient;
    private final SycaPayProperties properties;
    private final TelegramNotificationService telegramNotificationService;
    private final DynamicConfigurationService dynamicConfigurationService;

    /** Jour de la derniere alerte de seuil (une par jour et par instance au plus). */
    private final AtomicReference<LocalDate> derniereAlerte = new AtomicReference<>();

    @Scheduled(cron = "${sycapay.balance-report.cron:0 0 8 * * MON-SAT}", zone = "Africa/Abidjan")
    public void rapportMatinal() {
        if (actif()) {
            envoyerRapport("Solde SycaPay - debut de journee");
        }
    }

    @Scheduled(cron = "${sycapay.balance-alert.cron:0 0 9-19 * * MON-SAT}", zone = "Africa/Abidjan")
    public void surveillerSeuil() {
        if (!actif()) {
            return;
        }
        LocalDate aujourdhui = LocalDate.now(ZONE_ID);
        if (aujourdhui.equals(derniereAlerte.get())) {
            return;
        }
        lireSoldeWave().ifPresent(solde -> {
            if (solde.compareTo(BigDecimal.valueOf(seuil())) < 0) {
                derniereAlerte.set(aujourdhui);
                telegramNotificationService.envoyer("<b>Alerte - solde SycaPay bas</b>\n"
                        + "Solde Wave : <b>" + formater(solde) + " FCFA</b>, sous le seuil de " + formater(BigDecimal.valueOf(seuil()))
                        + " FCFA.\nLes prochains virements de commission risquent d'echouer : approvisionner le compte.");
            }
        });
    }

    /**
     * Lit le solde et l'envoie sur Telegram. Renvoie le texte envoye (pour l'appelant a la demande). Un
     * solde illisible est lui aussi signale sur Telegram : un silence ferait croire que tout va bien.
     */
    public String envoyerRapport(String titre) {
        String date = ZonedDateTime.now(ZONE_ID).format(FORMAT_DATE);
        String texte;
        if (!StringUtils.hasText(properties.getMerchantId())) {
            texte = "<b>" + titre + "</b> (" + date + ")\nSolde indisponible : identifiant marchand SycaPay non configure.";
        } else {
            Optional<SycaPayBalanceResponse> reponse = lireSolde();
            texte = reponse.map(r -> formaterRapport(titre, date, r))
                    .orElse("<b>" + titre + "</b> (" + date + ")\nSolde illisible : SycaPay n'a pas repondu. A verifier dans le back-office.");
        }
        telegramNotificationService.envoyer(texte);
        log.info("[SYCAPAY] rapport de solde envoye sur Telegram");
        return texte;
    }

    private String formaterRapport(String titre, String date, SycaPayBalanceResponse reponse) {
        StringBuilder texte = new StringBuilder("<b>").append(titre).append("</b> (").append(date).append(")\n");
        List<SycaPayBalanceEntry> soldes = reponse.resultats() == null ? List.of() : reponse.resultats();
        if (soldes.isEmpty()) {
            texte.append("Aucun compte retourne par SycaPay.\n");
        }
        for (SycaPayBalanceEntry entree : soldes) {
            texte.append(TelegramNotificationService.echapperHtml(entree.operateur())).append(" : <b>")
                    .append(formater(montant(entree.solde()))).append(" FCFA</b>\n");
        }
        long seuil = seuil();
        soldeWave(reponse).filter(solde -> solde.compareTo(BigDecimal.valueOf(seuil)) < 0)
                .ifPresent(solde -> texte.append("Attention : solde Wave sous le seuil d'alerte (")
                        .append(formater(BigDecimal.valueOf(seuil))).append(" FCFA).\n"));
        texte.append("Virements de commission : ").append(properties.isEnabled() ? "actives" : "desactives (SYCAPAY_ENABLED=false)");
        return texte.toString();
    }

    private Optional<BigDecimal> lireSoldeWave() {
        return lireSolde().flatMap(SycaPayBalanceReportService::soldeWave);
    }

    private Optional<SycaPayBalanceResponse> lireSolde() {
        try {
            return Optional.ofNullable(sycaPayClient.checkBalance());
        } catch (RuntimeException e) {
            log.warn("[SYCAPAY] solde illisible pour le rapport Telegram ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> soldeWave(SycaPayBalanceResponse reponse) {
        return reponse.resultats() == null ? Optional.empty() : reponse.resultats().stream()
                .filter(entree -> entree.operateur() != null && entree.operateur().toLowerCase(Locale.ROOT).contains("wave"))
                .map(entree -> montant(entree.solde()))
                .findFirst();
    }

    private static BigDecimal montant(String solde) {
        try {
            return new BigDecimal(solde == null ? "0" : solde.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** "1234567.50" -> "1 234 568" (FCFA entiers, espace simple : meme convention que les SMS). */
    static String formater(BigDecimal montant) {
        String brut = montant.setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder resultat = new StringBuilder();
        for (int i = 0; i < brut.length(); i++) {
            if (i > 0 && (brut.length() - i) % 3 == 0 && brut.charAt(i - 1) != '-') {
                resultat.append(' ');
            }
            resultat.append(brut.charAt(i));
        }
        return resultat.toString();
    }

    private boolean actif() {
        return Boolean.TRUE.equals(dynamicConfigurationService.getBoolean(CLE_ACTIVATION, true));
    }

    private long seuil() {
        Long valeur = dynamicConfigurationService.getLong(CLE_SEUIL, SEUIL_PAR_DEFAUT);
        return valeur == null ? SEUIL_PAR_DEFAUT : valeur;
    }
}
