package ci.assurzen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrat mobile lot 1 (docs/api-contracts/mobile-lot1-authentification.md, 2026-11-02). */
public final class MobileAuthDtos {

    public static final String PHONE_REGEX = "^(01|05|07)\\d{8}$";
    public static final String PHONE_MESSAGE = "Le numéro doit contenir 10 chiffres et commencer par 01, 05 ou 07";
    public static final String PIN_REGEX = "^\\d{6}$";
    public static final String PIN_MESSAGE = "Le code PIN doit contenir exactement 6 chiffres";
    public static final String CODE_REGEX = "^\\d{6}$";
    public static final String CODE_MESSAGE = "Le code doit contenir exactement 6 chiffres";

    private MobileAuthDtos() {
    }

    public record OtpRequest(
            @NotBlank(message = "Le numéro est obligatoire") @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "L'objet de la demande est obligatoire")
            @Pattern(regexp = "REGISTER|RESET_PIN", message = "purpose doit valoir REGISTER ou RESET_PIN") String purpose
    ) {
    }

    public record OtpVerifyRequest(
            @NotBlank(message = "Le numéro est obligatoire") @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "Le code est obligatoire") @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "L'objet de la demande est obligatoire")
            @Pattern(regexp = "REGISTER|RESET_PIN", message = "purpose doit valoir REGISTER ou RESET_PIN") String purpose
    ) {
    }

    public record OtpVerifyResponse(String verificationToken, long expiresInSeconds) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Le nom complet est obligatoire") @Size(max = 300, message = "Le nom est trop long") String fullName,
            @NotBlank(message = "Le numéro est obligatoire") @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "Le code PIN est obligatoire") @Pattern(regexp = PIN_REGEX, message = PIN_MESSAGE) String pin,
            @NotBlank(message = "La vérification du numéro est obligatoire") String verificationToken
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegisterResponse(UserResponse user, AuthTokens tokens, boolean linkedInsured, int linkedCount) {
    }

    public record PinResetRequest(
            @NotBlank(message = "Le numéro est obligatoire") @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "Le code est obligatoire") @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Le nouveau code PIN est obligatoire") @Pattern(regexp = PIN_REGEX, message = PIN_MESSAGE) String newPin
    ) {
    }

    public record PinRequest(
            @NotBlank(message = "Le code PIN est obligatoire") @Pattern(regexp = PIN_REGEX, message = PIN_MESSAGE) String pin
    ) {
    }

    public record DeleteRequestResponse(String reference) {
    }
}
