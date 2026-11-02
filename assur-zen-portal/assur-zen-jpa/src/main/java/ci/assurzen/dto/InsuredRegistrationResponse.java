package ci.assurzen.dto;

import java.util.List;

public record InsuredRegistrationResponse(
        InsuredResponse insured,
        List<ContractResponse> contracts,
        boolean welcomeSmsSent,
        int reminderSmsScheduled
) {
}
