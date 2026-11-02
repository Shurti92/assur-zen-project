package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "Error")
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
