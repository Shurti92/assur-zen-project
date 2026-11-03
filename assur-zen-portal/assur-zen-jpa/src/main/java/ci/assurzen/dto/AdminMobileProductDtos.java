package ci.assurzen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Parametrage du contenu mobile des produits (back-office, 2026-11-03). */
public final class AdminMobileProductDtos {

    private static final String IDENTIFIANT = "^[A-Za-z0-9_-]{1,40}$";

    private AdminMobileProductDtos() {
    }

    /** Garantie d'un produit. id facultatif (genere : g1, g2...) ; formulaIds = formules qui incluent cette garantie. */
    public record GuaranteeInput(
            @Pattern(regexp = "^$|" + IDENTIFIANT, message = "id : lettres, chiffres, tiret ou souligne (40 max)") String id,
            @NotBlank(message = "Le nom de la garantie est obligatoire") @Size(max = 150) String name,
            @NotBlank(message = "Le type de garantie est obligatoire")
            @Pattern(regexp = "MANDATORY|INCLUDED|OPTIONAL|TO_CONFIRM", message = "kind doit valoir MANDATORY, INCLUDED, OPTIONAL ou TO_CONFIRM") String kind,
            @Size(max = 500) String description,
            /** Plafond ou franchise, en texte libre. */
            @Size(max = 200) String limit,
            @Size(max = 20) List<@Pattern(regexp = IDENTIFIANT) String> formulaIds,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean visible
    ) {
    }

    /** Formule d'un produit (contenu, sans prix). id obligatoire. */
    public record FormulaInput(
            @NotBlank(message = "L'identifiant de la formule est obligatoire") @Pattern(regexp = IDENTIFIANT, message = "id : lettres, chiffres, tiret ou souligne (40 max)") String id,
            @NotBlank(message = "Le nom de la formule est obligatoire") @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean visible
    ) {
    }

    public record FaqInput(
            @NotBlank(message = "La question est obligatoire") @Size(max = 300) String question,
            @NotBlank(message = "La reponse est obligatoire") @Size(max = 2000) String answer
    ) {
    }

    /** Remplace TOUT le contenu mobile du produit (PUT). Les champs absents sont effaces. */
    public record ContentRequest(
            @NotNull(message = "visibleMobile est obligatoire") Boolean visibleMobile,
            @Size(max = 100) String shortName,
            @Pattern(regexp = "^$|VEHICLE|PERSON|PROPERTY|PROFESSIONAL", message = "family doit valoir VEHICLE, PERSON, PROPERTY ou PROFESSIONAL") String family,
            @Size(max = 2000) String description,
            @Size(max = 10, message = "10 avantages au maximum") List<@NotBlank @Size(max = 200) String> benefits,
            @Size(max = 4000) String howItWorks,
            @Size(max = 2000) String eligibility,
            @Size(max = 30, message = "30 garanties au maximum") List<@Valid GuaranteeInput> guarantees,
            @Size(max = 10, message = "10 formules au maximum") List<@Valid FormulaInput> formulas,
            @Size(max = 20, message = "20 exclusions au maximum") List<@NotBlank @Size(max = 300) String> exclusions,
            @Size(max = 20, message = "20 pieces au maximum") List<@NotBlank @Size(max = 200) String> requiredDocuments,
            @Size(max = 30, message = "30 questions au maximum") List<@Valid FaqInput> faq,
            @Pattern(regexp = "^$|^[a-z0-9_-]{1,40}$", message = "icon : minuscules, chiffres, tiret ou souligne") String icon,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean onlineSubscription
    ) {
    }

    public record Guarantee(String id, String name, String kind, String description, String limit, List<String> formulaIds,
                            Integer displayOrder, Boolean visible) {
    }

    public record Formula(String id, String name, String description, Integer displayOrder, Boolean visible) {
    }

    public record Faq(String question, String answer) {
    }

    public record ProductContent(
            Long productId,
            /** Identifiant lisible utilise par le mobile (auto, moto, sante...). */
            String productKey,
            String title,
            boolean productActive,
            /** false = aucun contenu mobile parametre : le produit n'apparait pas sur le mobile. */
            boolean configured,
            boolean visibleMobile,
            String shortName,
            String family,
            String description,
            List<String> benefits,
            String howItWorks,
            String eligibility,
            List<Guarantee> guarantees,
            List<Formula> formulas,
            List<String> exclusions,
            List<String> requiredDocuments,
            List<Faq> faq,
            String icon,
            int displayOrder,
            boolean onlineSubscription,
            Instant updatedAt,
            String updatedBy
    ) {
    }

    public record ProductContentList(List<ProductContent> items) {
    }

    public record OrderRequest(@NotNull(message = "La liste des produits est obligatoire") @Size(min = 1, max = 200) List<Long> productIds) {
    }
}
