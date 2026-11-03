package ci.assurzen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Devis en ligne (2026-11-03). Montants en F CFA entiers. */
public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record MotoSimulationRequest(
            @NotBlank(message = "Le type de vehicule est obligatoire")
            @Pattern(regexp = "MOTO|TRICYCLE", message = "vehicleType doit valoir MOTO ou TRICYCLE") String vehicleType,
            @NotNull(message = "La cylindree est obligatoire") @Min(value = 1, message = "La cylindree doit etre positive") @Max(value = 5000, message = "Cylindree invalide") Integer cylinderCc,
            @NotBlank(message = "La marque est obligatoire") @Size(max = 60) String brand,
            @NotBlank(message = "Le modele est obligatoire") @Size(max = 60) String model,
            @NotNull(message = "La duree est obligatoire") @Min(1) @Max(60) Integer durationMonths,
            @Size(max = 20) String registrationNumber,
            @Size(max = 30) String chassisNumber,
            @NotNull(message = "La date d'effet est obligatoire") LocalDate effectiveDate,
            /** Facultatif ; par defaut la formule unique du produit. */
            @Size(max = 40) String formulaId
    ) {
    }

    public record Coverage(String label, String amount) {
    }

    public record GuaranteeLine(String label, List<Coverage> details) {
    }

    /** total = netPremium - discount + taxes + accessories + fga + cedeao + attestation. */
    public record PremiumBreakdown(long netPremium, long taxes, long accessories, long fga, long cedeao, long attestation,
                                   long discount, long total, String currency) {
        /** Forme historique (moto) : sans CEDEAO, attestation ni remise. */
        public PremiumBreakdown(long netPremium, long taxes, long accessories, long fga, long total, String currency) {
            this(netPremium, taxes, accessories, fga, 0, 0, 0, total, currency);
        }
    }

    public record MotoSimulation(
            String productKey,
            String formulaId,
            String formulaName,
            String vehicleType,
            int durationMonths,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            PremiumBreakdown premium,
            List<GuaranteeLine> guarantees
    ) {
    }

    public record DurationOption(int months, long total) {
    }

    /** Assurance automobile VTC : criteres saisis par le client. discountPercent n'est accepte qu'au back-office. */
    public record VtcSimulationRequest(
            @NotNull(message = "La puissance fiscale est obligatoire") @Min(value = 1, message = "Puissance fiscale invalide") @Max(value = 60, message = "Puissance fiscale invalide") Integer fiscalPower,
            @NotBlank(message = "L'energie est obligatoire")
            @Pattern(regexp = "ESSENCE|DIESEL|HYBRIDE|ELECTRIQUE", message = "energy doit valoir ESSENCE, DIESEL, HYBRIDE ou ELECTRIQUE") String energy,
            @NotBlank(message = "La marque est obligatoire") @Size(max = 60) String brand,
            @NotBlank(message = "Le modele est obligatoire") @Size(max = 60) String model,
            @NotNull(message = "L'annee de mise en circulation est obligatoire") @Min(value = 1950, message = "Annee invalide") @Max(value = 2100, message = "Annee invalide") Integer firstRegistrationYear,
            @NotNull(message = "Le nombre de places est obligatoire") @Min(value = 1, message = "Nombre de places invalide") @Max(value = 60, message = "Nombre de places invalide") Integer seats,
            @NotNull(message = "Indiquez si le vehicule est deja declare en VTC") Boolean vtcDeclared,
            @Size(max = 60) String platform,
            @NotNull(message = "La duree est obligatoire") @Min(1) @Max(60) Integer durationMonths,
            @Size(max = 20) String registrationNumber,
            @Size(max = 30) String chassisNumber,
            @NotNull(message = "La date d'effet est obligatoire") LocalDate effectiveDate,
            @Size(max = 40) String formulaId,
            /** Remise en % sur la prime nette (back-office uniquement, plafonnee par QUOTE_VTC_MAX_DISCOUNT_PERCENT). */
            @DecimalMin(value = "0", message = "La remise ne peut pas etre negative") @DecimalMax(value = "100", message = "Remise invalide") java.math.BigDecimal discountPercent
    ) {
    }

    public record VtcSimulation(
            String productKey, String formulaId, String formulaName, int durationMonths, LocalDate effectiveDate, LocalDate expiryDate,
            PremiumBreakdown premium, List<GuaranteeLine> guarantees
    ) {
    }

    public record VtcFormInfo(String productKey, List<String> energies, List<DurationOption> durations, String formulaId, String formulaName,
                              LocalDate minEffectiveDate, List<String> brands, List<String> platforms,
                              Integer minFiscalPower, Integer minSeats, Integer maxSeats) {
    }

    public record VtcSettings(@NotNull @DecimalMin("0") @DecimalMax("100") java.math.BigDecimal maxDiscountPercent) {
    }

    public record BackOfficeVtcQuoteRequest(
            @NotBlank(message = "Le nom du prospect est obligatoire") @Size(max = 150) String prospectName,
            @NotBlank(message = "Le telephone du prospect est obligatoire")
            @Pattern(regexp = "^[0-9]{10}$", message = "Le telephone doit contenir 10 chiffres") String prospectPhone,
            @NotNull @Valid VtcSimulationRequest vehicle,
            @Email(message = "Adresse e-mail invalide") @Size(max = 150) String prospectEmail,
            Boolean sendEmail
    ) {
    }

    /** brands : liste proposee dans le formulaire (cle QUOTE_MOTO_BRANDS) ; vide = saisie libre. */
    public record MotoFormInfo(String productKey, List<String> vehicleTypes, List<DurationOption> durations, String formulaId,
                               String formulaName, LocalDate minEffectiveDate, List<String> brands) {
    }

    public record TariffInput(
            @NotBlank @Size(max = 40) String productKey,
            @NotBlank @Size(max = 40) String formulaId,
            @NotBlank @Pattern(regexp = "^(MOTO|TRICYCLE|VTC[A-Z0-9_-]{0,20})$", message = "vehicleCategory doit valoir MOTO, TRICYCLE ou VTC (suffixe facultatif pour une autre tranche)") String vehicleCategory,
            @NotNull @Min(1) @Max(60) Integer durationMonths,
            @NotNull @Min(0) Long netPremium,
            @NotNull @Min(0) Long taxes,
            @NotNull @Min(0) Long accessories,
            @NotNull @Min(0) Long fga,
            /** Facultatifs (VTC) : 0 par defaut. */
            @Min(0) Long cedeao,
            @Min(0) Long attestation,
            @NotNull @Min(1) Long total,
            Boolean active,
            /** Criteres VTC : puissance fiscale minimale, places min et max (facultatifs). */
            @Min(0) Integer minFiscalPower,
            @Min(0) Integer minSeats,
            @Min(0) Integer maxSeats
    ) {
    }

    public record TariffUpdate(@NotNull @Size(min = 1, max = 100) List<@Valid TariffInput> items) {
    }

    public record TariffView(Long id, String productKey, String formulaId, String vehicleCategory, int durationMonths, long netPremium,
                             long taxes, long accessories, long fga, long cedeao, long attestation, long total, boolean active,
                             Instant updatedAt, String updatedBy, Integer minFiscalPower, Integer minSeats, Integer maxSeats) {
    }

    public record TariffList(List<TariffView> items) {
    }

    /** Marques proposees dans le formulaire du devis moto (ordre d'affichage). */
    public record BrandList(List<String> brands) {
    }

    /** Plateformes VTC proposees dans le formulaire (Yango, Heetch...). */
    public record PlatformList(List<String> platforms) {
    }

    public record PlatformUpdate(@NotNull(message = "La liste des plateformes est obligatoire") @Size(max = 50, message = "50 plateformes au maximum")
                                 List<@NotBlank(message = "Une plateforme ne peut pas etre vide") @Size(max = 60, message = "Une plateforme fait 60 caracteres au maximum")
                                         @Pattern(regexp = "^[^,;\\n\\r]+$", message = "Une plateforme ne peut pas contenir de virgule, de point-virgule ni de retour a la ligne") String> platforms) {
    }

    public record BrandUpdate(@NotNull(message = "La liste des marques est obligatoire") @Size(max = 200, message = "200 marques au maximum")
                              List<@NotBlank(message = "Une marque ne peut pas etre vide") @Size(max = 60, message = "Une marque fait 60 caracteres au maximum")
                                      @Pattern(regexp = "^[^,;\n\r]+$", message = "Une marque ne peut pas contenir de virgule, de point-virgule ni de retour a la ligne") String> brands) {
    }

    /** Devis enregistre. status : CALCULE | ACCEPTE | EXPIRE. accepted indique si le client peut encore l'accepter. */
    public record QuoteView(
            String reference, String status, boolean canAccept, String productKey, String formulaId, String formulaName,
            String vehicleType, Integer cylinderCc, String brand, String model, String registrationNumber, String chassisNumber,
            int durationMonths, LocalDate effectiveDate, LocalDate expiryDate, LocalDate validUntil,
            PremiumBreakdown premium, List<GuaranteeLine> guarantees, Instant createdAt, Instant acceptedAt,
            String channel, String prospectName, String prospectPhone, Long createdByUserId,
            /** Champs VTC (null pour la moto). */
            Integer fiscalPower, String energy, Integer seats, Integer firstRegistrationYear, Boolean vtcDeclared, String platform,
            java.math.BigDecimal discountPercent,
            /** Envoi par e-mail (prospectEmail null et emailSentCount 0 pour les devis du mobile). */
            String prospectEmail, int emailSentCount, Instant lastEmailSentAt, String lastEmailTo, String lastEmailSentBy
    ) {
    }

    public record QuoteList(List<QuoteView> items) {
    }

    /** Envoi d'un devis par e-mail : email (defaut = prospectEmail), message personnel, PDF (defaut oui), copie a l'expediteur. */
    public record SendEmailRequest(
            @Email(message = "Adresse e-mail invalide") @Size(max = 150) String email,
            @Size(max = 500, message = "Le message fait 500 caracteres au maximum") String message,
            Boolean attachPdf,
            Boolean copyToMe
    ) {
    }

    public record EmailSent(Instant sentAt, String to, String sentBy, boolean withPdf, String copyTo) {
    }

    public record EmailHistory(List<EmailSent> items) {
    }

    /** Devis cree au back-office pour un prospect : memes champs que la simulation + identite du prospect. */
    public record BackOfficeQuoteRequest(
            @NotBlank(message = "Le nom du prospect est obligatoire") @Size(max = 150) String prospectName,
            @NotBlank(message = "Le telephone du prospect est obligatoire")
            @Pattern(regexp = "^[0-9]{10}$", message = "Le telephone doit contenir 10 chiffres") String prospectPhone,
            @NotNull @Valid MotoSimulationRequest vehicle,
            @Email(message = "Adresse e-mail invalide") @Size(max = 150) String prospectEmail,
            /** true = enregistrer puis envoyer au prospectEmail (sans effet si prospectEmail absent). */
            Boolean sendEmail
    ) {
    }
}
