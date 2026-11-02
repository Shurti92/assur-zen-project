package ci.assurzen.dto;

import java.util.List;

public record ParrainageValidationResponse(
        ParrainageResponse parrainage,
        boolean eligible,
        List<EligibiliteVerificationItem> verifications
) {
}
