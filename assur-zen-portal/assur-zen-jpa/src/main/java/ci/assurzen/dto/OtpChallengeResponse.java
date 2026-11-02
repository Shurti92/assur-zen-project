package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OtpChallengeResponse")
public record OtpChallengeResponse(
        boolean otpRequired,
        String challengeToken,
        String maskedPhone,
        long expiresInSeconds
) {
}
