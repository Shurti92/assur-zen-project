package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Demandes de modification / resiliation d'un contrat (mobile, 2026-11-02). */
public final class MobileContractRequestDtos {

    private MobileContractRequestDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "Le type de demande est obligatoire")
            @Pattern(regexp = "MODIFICATION|TERMINATION", message = "type doit valoir MODIFICATION ou TERMINATION") String type,
            @Size(max = 1000, message = "Le message ne doit pas depasser 1000 caracteres") String message
    ) {
    }

    /** expectedWithinHours est null tant que CONTRACT_REQUEST_RESPONSE_HOURS n'est pas configure (aucun engagement). */
    public record CreatedResponse(String reference, Integer expectedWithinHours) {
    }
}
