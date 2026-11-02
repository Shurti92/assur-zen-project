package ci.assurzen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InsuredRegistrationRequest(
        @NotNull @Valid InsuredRequest insured,
        @NotEmpty @Valid List<ContractSubscriptionRequest> contracts
) {
}
