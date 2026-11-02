package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse")
public record LoginResponse(
        UserResponse user,
        AuthTokens tokens
) {
}
