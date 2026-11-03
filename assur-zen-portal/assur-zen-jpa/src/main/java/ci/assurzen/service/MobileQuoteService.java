package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.Quote;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Devis en ligne du client mobile : enregistrement, consultation, acceptation, expiration (lot 2, 2026-11-03). */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileQuoteService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final QuoteService quoteService;
    private final QuoteRepository quotes;
    private final DynamicConfigurationService configuration;

    private LocalDate aujourdhui() {
        return LocalDate.now(ABIDJAN);
    }

    /** Calcule le devis (memes controles que la simulation) puis l'enregistre avec le prix fige. */
    @Transactional
    public QuoteView creerMoto(Long userId, MotoSimulationRequest r) {
        return vue(enregistrerMoto(r, q -> q.setUserId(userId)));
    }

    /** Cree et enregistre un devis moto ; extra renseigne le proprietaire / le canal avant la sauvegarde. */
    Quote enregistrerMoto(MotoSimulationRequest r, java.util.function.Consumer<Quote> extra) {
        MotoSimulation s = quoteService.simulerMoto(r);
        long jours = Math.max(1, configuration.getLong("QUOTE_VALIDITY_DAYS", 30L));
        Quote q = new Quote();
        q.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 20));
        q.setProductKey(s.productKey());
        q.setFormulaId(s.formulaId());
        q.setFormulaName(s.formulaName());
        q.setVehicleType(s.vehicleType());
        q.setCylinderCc(r.cylinderCc());
        q.setBrand(r.brand());
        q.setModel(r.model());
        q.setRegistrationNumber(blankToNull(r.registrationNumber()));
        q.setChassisNumber(blankToNull(r.chassisNumber()));
        q.setDurationMonths(s.durationMonths());
        q.setEffectiveDate(s.effectiveDate());
        q.setExpiryDate(s.expiryDate());
        q.setNetPremium(s.premium().netPremium());
        q.setTaxes(s.premium().taxes());
        q.setAccessories(s.premium().accessories());
        q.setFga(s.premium().fga());
        q.setTotal(s.premium().total());
        q.setValidUntil(plafonner(aujourdhui().plusDays(jours), s.effectiveDate()));
        extra.accept(q);
        q = quotes.save(q);
        q.setReference(String.format("DEV-%d-%06d", aujourdhui().getYear(), q.getId()));
        return quotes.save(q);
    }

    @Transactional
    public QuoteView creerVtc(Long userId, VtcSimulationRequest r) {
        return vue(enregistrerVtc(r, false, q -> q.setUserId(userId)));
    }

    /** Cree et enregistre un devis VTC ; remiseAutorisee = true au back-office uniquement. */
    Quote enregistrerVtc(VtcSimulationRequest r, boolean remiseAutorisee, java.util.function.Consumer<Quote> extra) {
        VtcSimulation s = quoteService.simulerVtc(r, remiseAutorisee);
        long jours = Math.max(1, configuration.getLong("QUOTE_VALIDITY_DAYS", 30L));
        Quote q = new Quote();
        q.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 20));
        q.setProductKey(s.productKey());
        q.setFormulaId(s.formulaId());
        q.setFormulaName(s.formulaName());
        q.setVehicleType("VTC");
        q.setFiscalPower(r.fiscalPower());
        q.setEnergy(r.energy());
        q.setSeats(r.seats());
        q.setFirstRegistrationYear(r.firstRegistrationYear());
        q.setVtcDeclared(r.vtcDeclared());
        q.setPlatform(blankToNull(r.platform()));
        q.setBrand(r.brand());
        q.setModel(r.model());
        q.setRegistrationNumber(blankToNull(r.registrationNumber()));
        q.setChassisNumber(blankToNull(r.chassisNumber()));
        q.setDurationMonths(s.durationMonths());
        q.setEffectiveDate(s.effectiveDate());
        q.setExpiryDate(s.expiryDate());
        q.setNetPremium(s.premium().netPremium());
        q.setTaxes(s.premium().taxes());
        q.setAccessories(s.premium().accessories());
        q.setFga(s.premium().fga());
        q.setCedeao(s.premium().cedeao());
        q.setAttestation(s.premium().attestation());
        q.setDiscountAmount(s.premium().discount());
        q.setDiscountPercent(r.discountPercent() == null || r.discountPercent().signum() == 0 ? null : r.discountPercent());
        q.setTotal(s.premium().total());
        q.setValidUntil(plafonner(aujourdhui().plusDays(jours), s.effectiveDate()));
        extra.accept(q);
        q = quotes.save(q);
        q.setReference(String.format("DEV-%d-%06d", aujourdhui().getYear(), q.getId()));
        return quotes.save(q);
    }

    /** Un devis ne peut pas etre accepte apres la date d'effet demandee. */
    private static LocalDate plafonner(LocalDate validite, LocalDate effet) {
        return validite.isAfter(effet) ? effet : validite;
    }

    @Transactional
    public QuoteList lister(Long userId) {
        return new QuoteList(quotes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::actualiser).map(this::vue).toList());
    }

    @Transactional
    public QuoteView detail(Long userId, String reference) {
        return vue(actualiser(trouver(userId, reference)));
    }

    @Transactional
    public QuoteView accepter(Long userId, String reference) {
        Quote q = actualiser(trouver(userId, reference));
        if ("EXPIRE".equals(q.getStatus())) {
            throw new MobileApiException(409, "QUOTE_EXPIRED", "Ce devis a expiré. Refaites une simulation pour obtenir un nouveau devis.");
        }
        if ("ACCEPTE".equals(q.getStatus())) {
            return vue(q);
        }
        q.setStatus("ACCEPTE");
        q.setAcceptedAt(Instant.now());
        return vue(quotes.save(q));
    }

    /** Passe les devis non acceptes a EXPIRE chaque nuit (la lecture applique aussi la regle a la volee). */
    @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Abidjan")
    @Transactional
    public void expirerLesDevis() {
        int n = quotes.expireJusqua(aujourdhui());
        if (n > 0) {
            log.info("[DEVIS] {} devis expire(s)", n);
        }
    }

    // ------------------------------------------------------------------

    private Quote trouver(Long userId, String reference) {
        return quotes.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new MobileApiException(404, "NOT_FOUND", "Devis introuvable."));
    }

    Quote actualiser(Quote q) {
        if ("CALCULE".equals(q.getStatus()) && q.getValidUntil().isBefore(aujourdhui())) {
            q.setStatus("EXPIRE");
            return quotes.save(q);
        }
        return q;
    }

    QuoteView vue(Quote q) {
        return new QuoteView(q.getReference(), q.getStatus(), "CALCULE".equals(q.getStatus()), q.getProductKey(), q.getFormulaId(),
                q.getFormulaName(), q.getVehicleType(), q.getCylinderCc(), q.getBrand(), q.getModel(), q.getRegistrationNumber(),
                q.getChassisNumber(), q.getDurationMonths(), q.getEffectiveDate(), q.getExpiryDate(), q.getValidUntil(),
                new PremiumBreakdown(q.getNetPremium(), q.getTaxes(), q.getAccessories(), q.getFga(), q.getCedeao(), q.getAttestation(),
                        q.getDiscountAmount(), q.getTotal(), "XOF"),
                QuoteService.VTC.equals(q.getProductKey()) ? QuoteService.garantiesTiersSimpleVtc() : QuoteService.garantiesTiersSimpleMoto(),
                q.getCreatedAt(), q.getAcceptedAt(),
                q.getChannel(), q.getProspectName(), q.getProspectPhone(), q.getCreatedByUserId(),
                q.getFiscalPower(), q.getEnergy(), q.getSeats(), q.getFirstRegistrationYear(), q.getVtcDeclared(), q.getPlatform(),
                q.getDiscountPercent());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
