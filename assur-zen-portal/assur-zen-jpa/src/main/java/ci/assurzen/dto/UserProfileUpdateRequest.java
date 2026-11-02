package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank @Size(max = 100) String username,
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
