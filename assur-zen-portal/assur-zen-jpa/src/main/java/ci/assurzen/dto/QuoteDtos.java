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

    public record PremiumBreakdown(long netPremium, long taxes, long accessories, long fga, long total, String currency) {
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

    /** brands : liste proposee dans le formulaire (cle QUOTE_MOTO_BRANDS) ; vide = saisie libre. */
    public record MotoFormInfo(String productKey, List<String> vehicleTypes, List<DurationOption> durations, String formulaId,
                               String formulaName, LocalDate minEffectiveDate, List<String> brands) {
    }

    public record TariffInput(
            @NotBlank @Size(max = 40) String productKey,
            @NotBlank @Size(max = 40) String formulaId,
            @NotBlank @Pattern(regexp = "MOTO|TRICYCLE") String vehicleCategory,
            @NotNull @Min(1) @Max(60) Integer durationMonths,
            @NotNull @Min(0) Long netPremium,
            @NotNull @Min(0) Long taxes,
            @NotNull @Min(0) Long accessories,
            @NotNull @Min(0) Long fga,
            @NotNull @Min(1) Long total,
            Boolean active
    ) {
    }

    public record TariffUpdate(@NotNull @Size(min = 1, max = 100) List<@Valid TariffInput> items) {
    }

    public record TariffView(Long id, String productKey, String formulaId, String vehicleCategory, int durationMonths, long netPremium,
                             long taxes, long accessories, long fga, long total, boolean active, Instant updatedAt, String updatedBy) {
    }

    public record TariffList(List<TariffView> items) {
    }

    /** Marques proposees dans le formulaire du devis moto (ordre d'affichage). */
    public record BrandList(List<String> brands) {
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
            String channel, String prospectName, String prospectPhone, Long createdByUserId
    ) {
    }

    public record QuoteList(List<QuoteView> items) {
    }

    /** Devis cree au back-office pour un prospect : memes champs que la simulation + identite du prospect. */
    public record BackOfficeQuoteRequest(
            @NotBlank(message = "Le nom du prospect est obligatoire") @Size(max = 150) String prospectName,
            @NotBlank(message = "Le telephone du prospect est obligatoire")
            @Pattern(regexp = "^[0-9]{10}$", message = "Le telephone doit contenir 10 chiffres") String prospectPhone,
            @NotNull @Valid MotoSimulationRequest vehicle
    ) {
    }
}
