package ci.assurzen.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordWithCodeRequest(
        @NotBlank @Size(max = 30) String username,
        @NotBlank @Size(min = 6, max = 6) String code,
        @NotBlank @Size(min = 6, max = 100) String newPassword,
        @NotBlank String confirmPassword
) {
    @AssertTrue(message = "Les mots de passe ne correspondent pas")
    public boolean isConfirmationValid() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
