package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthTokens")
public record AuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
