package ci.assurzen.dto;

import java.time.Instant;

/** 429 OTP_RESEND_TOO_SOON (2026-10-05) : meme forme qu'ErrorResponse + remainingSeconds, comme le 423. */
public record OtpResendTooSoonResponse(
        String code,
        String message,
        Instant timestamp,
        long remainingSeconds
) {
}
