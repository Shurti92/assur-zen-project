package ci.assurzen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommercialUpdateRequest(
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
        @NotNull LocalDate hireDate,
        LocalDate endDate
) {
}
