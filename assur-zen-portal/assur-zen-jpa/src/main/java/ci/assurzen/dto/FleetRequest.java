package ci.assurzen.dto;

import ci.assurzen.entities.FleetType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FleetRequest(
        @Size(max = 20) String fleetNumber,
        @NotNull Long clientId,
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull FleetType fleetType,
        Long produitId,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 150) String managerName,
        @Size(max = 30) String managerPhone,
        @Email @Size(max = 150) String managerEmail
) {
}
