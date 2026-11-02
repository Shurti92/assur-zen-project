package ci.assurzen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ParrainageRequest(
        @Size(max = 40) String code,
        Long parrainInsuredId,
        Long programmeId,
        @Size(max = 150) String filleulNom,
        @Email @Size(max = 150) String filleulEmail,
        @Size(max = 30) String filleulTelephone
) {
}
