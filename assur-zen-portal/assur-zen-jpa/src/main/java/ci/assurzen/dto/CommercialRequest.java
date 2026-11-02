package ci.assurzen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creation d'un commercial : cree AUSSI son compte de connexion (User+Profile, roles="COMMERCIAL")
 * via UserProfileService.create() - meme SMS de bienvenue que tout autre compte back-office, aucun
 * nouveau mecanisme d'authentification.
 */
public record CommercialRequest(
        @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 100) String username,
        @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 150) String firstname,
        @NotBlank @Size(max = 150) String lastname,
        @NotBlank @Size(max = 150) String email,
        @NotBlank @Size(max = 20) String phone,
        @NotNull Long villePartenaireId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal baseSalary,
        boolean transportBenefitEnabled,
        @DecimalMin(value = "0", inclusive = true) BigDecimal transportBenefitAmount,
        boolean phoneAllowanceEnabled,
        @DecimalMin(value = "0", inclusive = true) BigDecimal phoneAllowanceAmount,
        @NotNull LocalDate hireDate
) {
}
