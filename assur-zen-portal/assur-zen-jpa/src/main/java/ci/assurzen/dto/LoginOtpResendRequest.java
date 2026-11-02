package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginOtpResendRequest(
        @NotBlank String challengeToken
) {
}
