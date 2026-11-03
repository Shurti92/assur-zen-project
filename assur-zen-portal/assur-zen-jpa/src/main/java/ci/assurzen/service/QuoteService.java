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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Devis en ligne : grille tarifaire parametrable et simulation (lot 1, produit Moto, 2026-11-03). */
@Service
@RequiredArgsConstructor
public class QuoteService {

    public static final String MOTO = "moto";
    public static final String FORMULE_TIERS_SIMPLE = "tiers-simple-moto";
    private static final String NOM_FORMULE = "Tiers Simple Moto";

    private final QuoteTariffRepository tariffs;
    private final CommercialService commercialService;
    private final AuthService authService;
    private final ci.assurzen.services.DynamicConfigurationService configuration;
    private final ci.assurzen.repositories.DynamicConfigurationRepository configurationRepository;
    private final ci.assurzen.services.ConfigurationCacheService configurationCache;

    private static final String CLE_MARQUES = "QUOTE_MOTO_BRANDS";

    /** Grille initiale : tiers simple moto, 12 mois, identique pour MOTO et TRICYCLE (a confirmer par l'assureur). */
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
            // Total fixe 8 000 F. Prime nette 6 447 = valeur provisoire (6 375 annonces + 72 d'ecart) : A CONFIRMER.
            t.setNetPremium(6447L);
            t.setTaxes(925L);
            t.setAccessories(500L);
            t.setFga(128L);
            t.setTotal(8000L);
            t.setUpdatedBy("system");
            tariffs.save(t);
        }
    }

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public MotoFormInfo formulaireMoto() {
        List<DurationOption> durees = tariffs.findByProductKeyAndActiveTrue(MOTO).stream()
                .map(t -> new DurationOption(t.getDurationMonths(), t.getTotal()))
                .distinct().sorted(Comparator.comparingInt(DurationOption::months)).toList();
        return new MotoFormInfo(MOTO, List.of("MOTO", "TRICYCLE"), durees, FORMULE_TIERS_SIMPLE, NOM_FORMULE, LocalDate.now(), marques());
    }

    /** Marques proposees : cle QUOTE_MOTO_BRANDS, separees par des virgules ou des retours a la ligne, sans doublon (casse ignoree). */
    List<String> marques() {
        String brut = configuration.getString(CLE_MARQUES, "");
        if (brut == null || brut.isBlank()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, String> uniques = new java.util.LinkedHashMap<>();
        for (String m : brut.split("[,;\n\r]+")) {
            String marque = m.trim();
            if (!marque.isEmpty()) {
                uniques.putIfAbsent(marque.toLowerCase(java.util.Locale.ROOT), marque);
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

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public BrandList listerMarques() {
        commercialService.requireAdmin();
        return new BrandList(marques());
    }

    /** Remplace la liste (ordre conserve, doublons retires). Stockee dans la cle de configuration QUOTE_MOTO_BRANDS. */
    @Transactional
    public BrandList enregistrerMarques(BrandUpdate update) {
        commercialService.requireAdmin();
        java.util.LinkedHashMap<String, String> uniques = new java.util.LinkedHashMap<>();
        for (String m : update.brands()) {
            uniques.putIfAbsent(m.trim().toLowerCase(java.util.Locale.ROOT), m.trim());
        }
        var config = configurationRepository.findByConfigKey(CLE_MARQUES)
                .orElseThrow(() -> new MobileApiException(404, "NOT_FOUND", "Le parametre " + CLE_MARQUES + " n'existe pas encore."));
        config.setConfigValue(String.join(", ", uniques.values()));
        config.setUpdatedBy(authService.currentPrincipal().username());
        configurationRepository.save(config);
        configurationCache.evict(CLE_MARQUES);
        return new BrandList(List.copyOf(uniques.values()));
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
            long somme = in.netPremium() + in.taxes() + in.accessories() + in.fga();
            if (somme != in.total()) {
                throw new MobileApiException(400, "VALIDATION_FAILED",
                        "Le total (" + in.total() + ") doit etre egal a la somme des composantes (" + somme + ").", "total");
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
            t.setTotal(in.total());
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
                        t.getTaxes(), t.getAccessories(), t.getFga(), t.getTotal(), t.isActive(), t.getUpdatedAt(), t.getUpdatedBy())).toList());
    }
}
