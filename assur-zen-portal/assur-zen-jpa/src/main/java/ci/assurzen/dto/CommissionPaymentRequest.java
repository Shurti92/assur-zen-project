package ci.assurzen.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommissionPaymentRequest(
        @NotEmpty List<Long> contractIds,
        @Size(max = 255) String reference
) {
}
