package ci.assurzen.dto;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        String code,
        String firstname,
        String lastname,
        String phone,
        String role,
        boolean active,
        boolean expiration,
        Long version,
        Instant creation,
        Instant lastUpdate,
        Long userId,
        Long villePartenaireId,
        String bio,
        String facebookUrl,
        String twitterUrl,
        String linkedinUrl,
        String instagramUrl,
        String country,
        String city,
        String state,
        String postalCode,
        String taxId,
        boolean twoFactorEnabled,
        String email,
        String sector,
        String avatarUrl,
        String langueCode
) {
}
