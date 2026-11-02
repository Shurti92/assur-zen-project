package ci.assurzen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        String code,
        @NotBlank @Size(max = 150) String firstname,
        @NotBlank @Size(max = 150) String lastname,
        @Size(max = 20) String phone,
        @Size(max = 20) String role,
        @NotNull Long userId,
        Long villePartenaireId,
        Boolean active,
        Boolean expiration,
        @Email @Size(max = 150) String email,
        @Size(max = 150) String sector,
        @Size(max = 255) String avatarUrl,
        @Size(max = 10) String langueCode,
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
