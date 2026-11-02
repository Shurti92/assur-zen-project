package ci.assurzen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ProfilePatch(
        @Size(max = 255) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 150) String sector,
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
        Boolean twoFactorEnabled,
        @Size(max = 10) String langueCode
) {
}
