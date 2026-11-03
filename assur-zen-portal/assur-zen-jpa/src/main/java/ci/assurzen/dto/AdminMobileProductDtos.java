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

    private AdminMobileProductDtos() {
    }

    public record GuaranteeInput(
            @NotBlank(message = "Le nom de la garantie est obligatoire") @Size(max = 150) String name,
            @NotBlank(message = "Le type de garantie est obligatoire")
            @Pattern(regexp = "MANDATORY|INCLUDED|OPTIONAL|TO_CONFIRM", message = "kind doit valoir MANDATORY, INCLUDED, OPTIONAL ou TO_CONFIRM") String kind
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
            @Size(max = 20, message = "20 garanties au maximum") List<@Valid GuaranteeInput> guarantees,
            @Pattern(regexp = "^$|^[a-z0-9_-]{1,40}$", message = "icon : minuscules, chiffres, tiret ou souligne") String icon,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean onlineSubscription
    ) {
    }

    public record Guarantee(String name, String kind) {
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
