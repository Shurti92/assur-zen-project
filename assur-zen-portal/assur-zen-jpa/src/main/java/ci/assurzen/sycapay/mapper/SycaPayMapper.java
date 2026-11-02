package ci.assurzen.sycapay.mapper;

import ci.assurzen.sycapay.dto.SycaPayTransactionResponse;
import ci.assurzen.sycapay.entity.SycaPayTransaction;

public final class SycaPayMapper {

    private SycaPayMapper() {
    }

    public static SycaPayTransactionResponse toResponse(SycaPayTransaction transaction) {
        return new SycaPayTransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getContractId(),
                transaction.getBeneficiaryType(),
                transaction.getCommercialId(),
                transaction.getPartenaireId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBeneficiaryName(),
                transaction.getBeneficiaryPhone(),
                transaction.getStatus(),
                transaction.getSycapayReference(),
                transaction.getSycapayTransactionId(),
                transaction.getSycapayResponseCode(),
                transaction.getSycapayResponseMessage(),
                transaction.getRequestDate(),
                transaction.getProcessingDate(),
                transaction.getCompletedDate(),
                transaction.getAttemptCount(),
                transaction.getErrorCode(),
                transaction.getErrorMessage(),
                transaction.isTestMode(),
                transaction.getPartialPaymentRequestId(),
                transaction.getCommercialPerformanceId()
        );
    }
}
