package ci.assurzen.dto;

import ci.assurzen.entities.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InsuredRequest(
        @NotBlank
        @Size(max = 100)
        String nom,

        @NotBlank
        @Size(max = 200)
        String prenoms,

        @Size(max = 30)
        String clientPhone,

        @Size(max = 120)
        String clientCity,

        @Email
        @Size(max = 150)
        String clientEmail,

        TypeClient typeClient,

        Long insuredId,

        @Size(max = 30)
        String clientPhone2
) {
}
