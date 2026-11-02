package ci.assurzen.sycapay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Mode TEST des virements SycaPay (2026-09-24), HORS PRODUCTION UNIQUEMENT : tout virement de commission
 * (commercial ou partenaire, manuel, IMMEDIAT automatique ou nouvelle tentative) est redirige vers un
 * numero de test et un montant de test, pour tester le parcours complet sans verser de vraies commissions.
 *
 * Garde-fous :
 * - lu UNE FOIS au demarrage depuis l'environnement (SYCAPAY_TEST_MODE_ENABLED / _PHONE / _AMOUNT), JAMAIS
 *   depuis la configuration dynamique : aucun admin ne peut l'allumer ni l'eteindre depuis l'ecran ;
 * - ignore (log ERROR) si un profil Spring contenant "prod" est actif, meme si la variable est a true ;
 * - demarrage REFUSE si le mode est demande hors prod sans numero valide ni montant positif : jamais de
 *   mode test "a moitie configure" qui laisserait partir un vrai virement ;
 * - numero de test jamais ecrit dans le code (variable d'environnement uniquement).
 */
@Component
@Slf4j
public class SycaPayModeTest {

    private final boolean actif;
    private final String telephone;
    private final Long montant;

    @Autowired
    public SycaPayModeTest(@Value("${sycapay.test-mode.enabled:false}") boolean demande,
                           @Value("${sycapay.test-mode.beneficiary-phone:}") String telephone,
                           @Value("${sycapay.test-mode.amount:100}") long montant,
                           Environment environment) {
        this(demande, telephone, montant, environment.getActiveProfiles());
    }

    public SycaPayModeTest(boolean demande, String telephone, long montant, String[] profilsActifs) {
        boolean production = Arrays.stream(profilsActifs == null ? new String[0] : profilsActifs)
                .anyMatch(p -> p != null && p.toLowerCase(Locale.ROOT).contains("prod"));
        if (demande && production) {
            log.error("[SYCAPAY] MODE TEST demande (SYCAPAY_TEST_MODE_ENABLED=true) alors qu'un profil de PRODUCTION est actif ({}) "
                    + "- demande IGNOREE, les virements restent reels", String.join(",", profilsActifs));
        }
        this.actif = demande && !production;
        if (actif) {
            if (!StringUtils.hasText(telephone) || !telephone.trim().matches("\\+?[0-9]{8,15}") || montant <= 0) {
                throw new IllegalStateException("Mode test SycaPay demande mais SYCAPAY_TEST_MODE_PHONE (8 a 15 chiffres) "
                        + "et/ou SYCAPAY_TEST_MODE_AMOUNT (> 0) invalides - demarrage refuse");
            }
            log.warn("[SYCAPAY] MODE TEST ACTIF : tout virement de commission est redirige vers {} pour {} XOF (profils {})",
                    telephone.trim(), montant, String.join(",", profilsActifs));
        }
        this.telephone = actif ? telephone.trim() : null;
        this.montant = actif ? montant : null;
    }

    public boolean isActif() {
        return actif;
    }

    /** Numero de test, null hors mode test. */
    public String getTelephone() {
        return telephone;
    }

    /** Montant de test (XOF), null hors mode test. */
    public Long getMontant() {
        return montant;
    }
}
