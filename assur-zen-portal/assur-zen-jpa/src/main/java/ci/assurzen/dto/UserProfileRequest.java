package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank @Size(max = 100) String username,
        /**
         * Optionnel : si absent/vide, un mot de passe temporaire est genere aleatoirement par le
         * backend (contrat "Creation de comptes utilisateurs" §4.1 - comptes back-office, surface
         * d'attaque plus sensible que le mot de passe fixe des partenaires). Renvoye une seule fois
         * dans UserProfileResponse.temporaryPassword.
         */
        @Size(min = 6, max = 100) String password,
        String roles,
        Long typeProfileId,
        String code,
        @NotBlank @Size(max = 150) String firstname,
        @NotBlank @Size(max = 150) String lastname,
        @Size(max = 20) String phone,
        @Size(max = 20) String role,
        @Size(max = 150) String email,
        @Size(max = 150) String sector,
        Long villePartenaireId,
        Boolean active,
        @Size(max = 1000) String bio,
        @Size(max = 255) String facebookUrl,
        @Size(max = 255) String twitterUrl,
        @Size(max = 255) String linkedinUrl,
        @Size(max = 255) String instagramUrl,
        @Size(max = 100) String country,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 50) String taxId,
        Boolean twoFactorEnabled
) {
}
