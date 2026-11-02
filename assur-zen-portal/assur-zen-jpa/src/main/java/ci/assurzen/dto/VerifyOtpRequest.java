package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Le code doit contenir exactement 6 chiffres") String code
) {
}
