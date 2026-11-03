package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Catalogue et demande de rappel de l'application mobile, accessibles sans compte (demande n°5, 2026-11-03). */
public final class PublicMobileDtos {

    private PublicMobileDtos() {
    }

    /** kind : MANDATORY, INCLUDED, OPTIONAL ou TO_CONFIRM. */
    public record Guarantee(String name, String kind) {
    }

    /**
     * Produit du catalogue mobile. Tout le contenu (description, avantages, fonctionnement, eligibilite, garanties, icone,
     * ordre) est parametre depuis le back-office ; un champ null ou une liste vide = non communique.
     */
    public record Product(
            String id,
            String title,
            String shortName,
            String description,
            String family,
            boolean onlineSubscription,
            List<String> benefits,
            String howItWorks,
            String eligibility,
            List<Guarantee> guarantees,
            String icon,
            int displayOrder
    ) {
    }

    public record ProductList(List<Product> items) {
    }

    public record CallbackRequest(
            @NotBlank(message = "Le numero est obligatoire")
            @Pattern(regexp = MobileAuthDtos.PHONE_REGEX, message = MobileAuthDtos.PHONE_MESSAGE) String phone,
            @NotBlank(message = "Le creneau est obligatoire")
            @Pattern(regexp = "ASAP|MORNING|AFTERNOON|EVENING", message = "slot doit valoir ASAP, MORNING, AFTERNOON ou EVENING") String slot,
            @Size(max = 60) String productId
    ) {
    }

    /** expectedWithinMinutes est null tant que le delai reel n'est pas configure (CALLBACK_EXPECTED_MINUTES). */
    public record CallbackCreated(String reference, Integer expectedWithinMinutes) {
    }
}
