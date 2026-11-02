package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CommercialResponse(
        Long id,
        String employeeCode,
        Long profileId,
        Long userId,
        String username,
        String firstname,
        String lastname,
        String email,
        String phone,
        Long villePartenaireId,
        String villePartenaireNom,
        BigDecimal baseSalary,
        boolean transportBenefitEnabled,
        BigDecimal transportBenefitAmount,
        boolean phoneAllowanceEnabled,
        BigDecimal phoneAllowanceAmount,
        LocalDate hireDate,
        LocalDate endDate,
        StatutCommercial statut,
        Instant createdAt,
        Instant updatedAt,
        /** Present UNE SEULE FOIS, uniquement sur la reponse de create() - meme convention que UserProfileResponse.temporaryPassword. */
        String temporaryPassword,
        Boolean notificationEnvoyee
) {
}
