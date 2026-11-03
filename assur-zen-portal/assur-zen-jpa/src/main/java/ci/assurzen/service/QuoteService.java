package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.QuoteTariff;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteTariffRepository;
import ci.assurzen.services.CommercialService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Devis en ligne : grilles tarifaires parametrables et simulation (moto, assurance automobile VTC, 2026-11-03). */
@Service
@RequiredArgsConstructor
public class QuoteService {

    public static final String MOTO = "moto";
    public static final String FORMULE_TIERS_SIMPLE = "tiers-simple-moto";
    private static final String NOM_FORMULE = "Tiers Simple Moto";

    public static final String VTC = "automobile-vtc";
    public static final String FORMULE_VTC = "tiers-simple-vtc";
    private static final String NOM_FORMULE_VTC = "Tiers Simple VTC";
    private static final List<String> ENERGIES = List.of("ESSENCE", "DIESEL", "HYBRIDE", "ELECTRIQUE");

    private static final String CLE_MARQUES = "QUOTE_MOTO_BRANDS";
    private static final String CLE_MARQUES_VTC = "QUOTE_VTC_BRANDS";
    private static final String CLE_PLATEFORMES_VTC = "QUOTE_VTC_PLATFORMS";
    private static final String CLE_REMISE_MAX_VTC = "QUOTE_VTC_MAX_DISCOUNT_PERCENT";

    private final QuoteTariffRepository tariffs;
    private final CommercialService commercialService;
    private final AuthService authService;
    private final ci.assurzen.services.DynamicConfigurationService configuration;
    private final ci.assurzen.repositories.DynamicConfigurationRepository configurationRepository;
    private final ci.assurzen.services.ConfigurationCacheService configurationCache;

    /** Grilles initiales (a confirmer par l'assureur) : tiers simple moto 12 mois, tiers simple VTC 1/3/6/12 mois. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialiserGrille() {
        for (String categorie : List.of("MOTO", "TRICYCLE")) {
            if (tariffs.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(MOTO, FORMULE_TIERS_SIMPLE, categorie, 12).isPresent()) {
                continue;
            }
            QuoteTariff t = new QuoteTariff();
            t.setProductKey(MOTO);
            t.setFormulaId(FORMULE_TIERS_SIMPLE);
            t.setVehicleCategory(categorie);
            t.setDurationMonths(12);
            // Total fixe 8 000 F. Prime nette 6 447 confirmee par l'utilisateur.
            t.setNetPremium(6447L);
            t.setTaxes(925L);
            t.setAccessories(500L);
            t.setFga(128L);
            t.setTotal(8000L);
            t.setUpdatedBy("system");
            tariffs.save(t);
        }
        // VTC, 3 CV et plus, 4 a 8 places, usage public. Totaux fournis ; frais fixes 4 304 F (accessoires 2 024,
        // CEDEAO 1 000, attestation 1 000, FGA 280) ; taxes non communiquees = 0 (prime nette = total - frais fixes).
        Map<Integer, Long> totaux = Map.of(1, 23800L, 3, 54160L, 6, 98732L, 12, 185667L);
        for (Map.Entry<Integer, Long> e : totaux.entrySet()) {
            if (tariffs.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(VTC, FORMULE_VTC, "VTC", e.getKey()).isPresent()) {
                continue;
            }
            QuoteTariff t = new QuoteTariff();
            t.setProductKey(VTC);
            t.setFormulaId(FORMULE_VTC);
            t.setVehicleCategory("VTC");
            t.setDurationMonths(e.getKey());
            t.setAccessories(2024L);
            t.setCedeao(1000L);
            t.setAttestation(1000L);
            t.setFga(280L);
            t.setTaxes(0L);
            t.setNetPremium(e.getValue() - 4304L);
            t.setTotal(e.getValue());
            t.setMinFiscalPower(3);
            t.setMinSeats(4);
            t.setMaxSeats(8);
            t.setUpdatedBy("system");
            tariffs.save(t);
        }
    }

    // ------------------------------------------------------------------ public : moto

    @Transactional(readOnly = true)
    public MotoFormInfo formulaireMoto() {
        List<DurationOption> durees = tariffs.findByProductKeyAndActiveTrue(MOTO).stream()
                .map(t -> new DurationOption(t.getDurationMonths(), t.getTotal()))
                .distinct().sorted(Comparator.comparingInt(DurationOption::months)).toList();
        return new MotoFormInfo(MOTO, List.of("MOTO", "TRICYCLE"), durees, FORMULE_TIERS_SIMPLE, NOM_FORMULE, LocalDate.now(), marques());
    }

    /** Marques moto : cle QUOTE_MOTO_BRANDS. */
    List<String> marques() {
        return liste(CLE_MARQUES);
    }

    /** Liste stockee dans une cle de configuration : separee par des virgules ou retours a la ligne, sans doublon (casse ignoree). */
    List<String> liste(String cle) {
        String brut = configuration.getString(cle, "");
        if (brut == null || brut.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, String> uniques = new LinkedHashMap<>();
        for (String m : brut.split("[,;\n\r]+")) {
            String valeur = m.trim();
            if (!valeur.isEmpty()) {
                uniques.putIfAbsent(valeur.toLowerCase(Locale.ROOT), valeur);
            }
        }
        return List.copyOf(uniques.values());
    }

    @Transactional(readOnly = true)
    public MotoSimulation simulerMoto(MotoSimulationRequest r) {
        if (!StringUtils.hasText(r.registrationNumber()) && !StringUtils.hasText(r.chassisNumber())) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Renseignez le numero d'immatriculation ou le numero de chassis.", "registrationNumber");
        }
        if (r.effectiveDate().isBefore(LocalDate.now())) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "La date d'effet doit etre aujourd'hui ou une date future.", "effectiveDate");
        }
        String formule = StringUtils.hasText(r.formulaId()) ? r.formulaId() : FORMULE_TIERS_SIMPLE;
        if (!FORMULE_TIERS_SIMPLE.equals(formule)) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Cette formule n'existe pas.", "formulaId");
        }
        QuoteTariff t = tariffs.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(MOTO, formule, r.vehicleType(), r.durationMonths())
                .filter(QuoteTariff::isActive)
                .orElseThrow(() -> new MobileApiException(400, "VALIDATION_FAILED", "Cette duree n'est pas proposee pour ce vehicule.", "durationMonths"));
        return new MotoSimulation(MOTO, formule, NOM_FORMULE, r.vehicleType(), t.getDurationMonths(), r.effectiveDate(),
                r.effectiveDate().plusMonths(t.getDurationMonths()).minusDays(1),
                new PremiumBreakdown(t.getNetPremium(), t.getTaxes(), t.getAccessories(), t.getFga(), t.getTotal(), "XOF"),
                garantiesTiersSimpleMoto());
    }

    static List<GuaranteeLine> garantiesTiersSimpleMoto() {
        return List.of(
                new GuaranteeLine("Responsabilité civile - Dommages corporels", List.of()),
                new GuaranteeLine("Responsabilité civile - Dommages matériels", List.of()),
                new GuaranteeLine("Défense et Recours", List.of()),
                new GuaranteeLine("Individuel conducteur", List.of(
                        new Coverage("Décès", "500 000 F CFA"),
                        new Coverage("Infirmité permanente totale (IPT)", "500 000 F CFA"),
                        new Coverage("Frais médicaux", "50 000 F CFA"))));
    }

    // ------------------------------------------------------------------ public : assurance automobile VTC

    @Transactional(readOnly = true)
    public VtcFormInfo formulaireVtc() {
        List<QuoteTariff> grille = tariffs.findByProductKeyAndActiveTrue(VTC).stream().filter(t -> t.getVehicleCategory().startsWith("VTC")).toList();
        List<DurationOption> durees = grille.stream().map(t -> new DurationOption(t.getDurationMonths(), t.getTotal()))
                .distinct().sorted(Comparator.comparingInt(DurationOption::months)).toList();
        return new VtcFormInfo(VTC, ENERGIES, durees, FORMULE_VTC, NOM_FORMULE_VTC, LocalDate.now(), liste(CLE_MARQUES_VTC), liste(CLE_PLATEFORMES_VTC),
                grille.stream().map(QuoteTariff::getMinFiscalPower).filter(java.util.Objects::nonNull).min(Integer::compare).orElse(null),
                grille.stream().map(QuoteTariff::getMinSeats).filter(java.util.Objects::nonNull).min(Integer::compare).orElse(null),
                grille.stream().map(QuoteTariff::getMaxSeats).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(null));
    }

    /**
     * Calcule la prime VTC. remiseAutorisee = true uniquement au back-office : la remise (en % de la prime nette) est alors
     * plafonnee par QUOTE_VTC_MAX_DISCOUNT_PERCENT (10 par defaut).
     */
    @Transactional(readOnly = true)
    public VtcSimulation simulerVtc(VtcSimulationRequest r, boolean remiseAutorisee) {
        if (!StringUtils.hasText(r.registrationNumber()) && !StringUtils.hasText(r.chassisNumber())) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Renseignez le numero d'immatriculation ou le numero de chassis.", "registrationNumber");
        }
        if (r.effectiveDate().isBefore(LocalDate.now())) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "La date d'effet doit etre aujourd'hui ou une date future.", "effectiveDate");
        }
        if (r.firstRegistrationYear() > LocalDate.now().getYear() + 1) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "L'annee de mise en circulation ne peut pas etre dans le futur.", "firstRegistrationYear");
        }
        String formule = StringUtils.hasText(r.formulaId()) ? r.formulaId() : FORMULE_VTC;
        if (!FORMULE_VTC.equals(formule)) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Cette formule n'existe pas.", "formulaId");
        }
        BigDecimal pourcentage = r.discountPercent() == null ? BigDecimal.ZERO : r.discountPercent();
        if (pourcentage.signum() > 0) {
            if (!remiseAutorisee) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Aucune remise n'est possible pour ce devis.", "discountPercent");
            }
            BigDecimal max = remiseMaxVtc();
            if (pourcentage.compareTo(max) > 0) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "La remise ne peut pas depasser " + max.stripTrailingZeros().toPlainString() + " %.", "discountPercent");
            }
        }
        List<QuoteTariff> duree = tariffs.findByProductKeyAndActiveTrue(VTC).stream()
                .filter(t -> formule.equals(t.getFormulaId()) && t.getVehicleCategory().startsWith("VTC") && t.getDurationMonths().equals(r.durationMonths()))
                .toList();
        if (duree.isEmpty()) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Cette duree n'est pas proposee.", "durationMonths");
        }
        QuoteTariff t = duree.stream()
                .filter(x -> x.getMinFiscalPower() == null || r.fiscalPower() >= x.getMinFiscalPower())
                .filter(x -> (x.getMinSeats() == null || r.seats() >= x.getMinSeats()) && (x.getMaxSeats() == null || r.seats() <= x.getMaxSeats()))
                .max(Comparator.comparing((QuoteTariff x) -> x.getMinFiscalPower() == null ? 0 : x.getMinFiscalPower())
                        .thenComparing(x -> x.getMinSeats() == null ? 0 : x.getMinSeats()))
                .orElseThrow(() -> new MobileApiException(400, "VALIDATION_FAILED",
                        "Aucun tarif pour ce vehicule : verifiez la puissance fiscale et le nombre de places.", "seats"));
        long remise = BigDecimal.valueOf(t.getNetPremium()).multiply(pourcentage).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
        long total = t.getNetPremium() - remise + t.getTaxes() + t.getAccessories() + t.getFga() + t.getCedeao() + t.getAttestation();
        return new VtcSimulation(VTC, formule, NOM_FORMULE_VTC, t.getDurationMonths(), r.effectiveDate(),
                r.effectiveDate().plusMonths(t.getDurationMonths()).minusDays(1),
                new PremiumBreakdown(t.getNetPremium(), t.getTaxes(), t.getAccessories(), t.getFga(), t.getCedeao(), t.getAttestation(), remise, total, "XOF"),
                garantiesTiersSimpleVtc());
    }

    static List<GuaranteeLine> garantiesTiersSimpleVtc() {
        return List.of(
                new GuaranteeLine("Responsabilité civile", List.of()),
                new GuaranteeLine("Défense et Recours", List.of()));
    }

    BigDecimal remiseMaxVtc() {
        try {
            return new BigDecimal(configuration.getString(CLE_REMISE_MAX_VTC, "10").trim());
        } catch (RuntimeException e) {
            return BigDecimal.TEN;
        }
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public BrandList listerMarques() {
        return listerMarques(MOTO);
    }

    @Transactional(readOnly = true)
    public BrandList listerMarques(String produit) {
        commercialService.requireAdmin();
        return new BrandList(liste(cleMarques(produit)));
    }

    @Transactional
    public BrandList enregistrerMarques(BrandUpdate update) {
        return enregistrerMarques(MOTO, update);
    }

    /** Remplace la liste des marques du produit (moto ou vtc) ; ordre conserve, doublons retires. */
    @Transactional
    public BrandList enregistrerMarques(String produit, BrandUpdate update) {
        commercialService.requireAdmin();
        return new BrandList(ecrireListe(cleMarques(produit), update.brands()));
    }

    @Transactional(readOnly = true)
    public PlatformList listerPlateformes() {
        commercialService.requireAdmin();
        return new PlatformList(liste(CLE_PLATEFORMES_VTC));
    }

    @Transactional
    public PlatformList enregistrerPlateformes(PlatformUpdate update) {
        commercialService.requireAdmin();
        return new PlatformList(ecrireListe(CLE_PLATEFORMES_VTC, update.platforms()));
    }

    @Transactional(readOnly = true)
    public VtcSettings lireParametresVtc() {
        commercialService.requireAdmin();
        return new VtcSettings(remiseMaxVtc());
    }

    /** Plafond de remise lisible par tout utilisateur du back-office (ADMIN et COMMERCIAL) : le controleur est protege par la securite. */
    @Transactional(readOnly = true)
    public VtcSettings limiteRemiseVtc() {
        return new VtcSettings(remiseMaxVtc());
    }

    @Transactional
    public VtcSettings enregistrerParametresVtc(VtcSettings settings) {
        commercialService.requireAdmin();
        ecrireConfiguration(CLE_REMISE_MAX_VTC, settings.maxDiscountPercent().stripTrailingZeros().toPlainString());
        return new VtcSettings(remiseMaxVtc());
    }

    private static String cleMarques(String produit) {
        if ("moto".equalsIgnoreCase(produit)) {
            return CLE_MARQUES;
        }
        if ("vtc".equalsIgnoreCase(produit)) {
            return CLE_MARQUES_VTC;
        }
        throw new MobileApiException(404, "NOT_FOUND", "Produit inconnu : moto ou vtc.");
    }

    private List<String> ecrireListe(String cle, List<String> valeurs) {
        LinkedHashMap<String, String> uniques = new LinkedHashMap<>();
        for (String m : valeurs) {
            uniques.putIfAbsent(m.trim().toLowerCase(Locale.ROOT), m.trim());
        }
        ecrireConfiguration(cle, String.join(", ", uniques.values()));
        return List.copyOf(uniques.values());
    }

    private void ecrireConfiguration(String cle, String valeur) {
        var config = configurationRepository.findByConfigKey(cle)
                .orElseThrow(() -> new MobileApiException(404, "NOT_FOUND", "Le parametre " + cle + " n'existe pas encore."));
        config.setConfigValue(valeur);
        config.setUpdatedBy(authService.currentPrincipal().username());
        configurationRepository.save(config);
        configurationCache.evict(cle);
    }

    @Transactional(readOnly = true)
    public TariffList listerGrille() {
        commercialService.requireAdmin();
        return toutes();
    }

    @Transactional
    public TariffList enregistrerGrille(TariffUpdate update) {
        commercialService.requireAdmin();
        for (TariffInput in : update.items()) {
            long cedeao = in.cedeao() == null ? 0 : in.cedeao();
            long attestation = in.attestation() == null ? 0 : in.attestation();
            long somme = in.netPremium() + in.taxes() + in.accessories() + in.fga() + cedeao + attestation;
            if (somme != in.total()) {
                throw new MobileApiException(400, "VALIDATION_FAILED",
                        "Le total (" + in.total() + ") doit etre egal a la somme des composantes (" + somme + ").", "total");
            }
            if (in.minSeats() != null && in.maxSeats() != null && in.minSeats() > in.maxSeats()) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Le nombre de places minimal depasse le maximal.", "minSeats");
            }
            QuoteTariff t = tariffs.findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(
                    in.productKey(), in.formulaId(), in.vehicleCategory(), in.durationMonths()).orElse(null);
            if (t == null) {
                t = new QuoteTariff();
                t.setProductKey(in.productKey());
                t.setFormulaId(in.formulaId());
                t.setVehicleCategory(in.vehicleCategory());
                t.setDurationMonths(in.durationMonths());
            }
            t.setNetPremium(in.netPremium());
            t.setTaxes(in.taxes());
            t.setAccessories(in.accessories());
            t.setFga(in.fga());
            t.setCedeao(cedeao);
            t.setAttestation(attestation);
            t.setTotal(in.total());
            t.setMinFiscalPower(in.minFiscalPower());
            t.setMinSeats(in.minSeats());
            t.setMaxSeats(in.maxSeats());
            t.setActive(in.active() == null || in.active());
            t.setUpdatedAt(Instant.now());
            t.setUpdatedBy(authService.currentPrincipal().username());
            tariffs.save(t);
        }
        return toutes();
    }

    private TariffList toutes() {
        return new TariffList(tariffs.findAllByOrderByProductKeyAscFormulaIdAscVehicleCategoryAscDurationMonthsAsc().stream().map(t ->
                new TariffView(t.getId(), t.getProductKey(), t.getFormulaId(), t.getVehicleCategory(), t.getDurationMonths(), t.getNetPremium(),
                        t.getTaxes(), t.getAccessories(), t.getFga(), t.getCedeao(), t.getAttestation(), t.getTotal(), t.isActive(),
                        t.getUpdatedAt(), t.getUpdatedBy(), t.getMinFiscalPower(), t.getMinSeats(), t.getMaxSeats())).toList());
    }
}
