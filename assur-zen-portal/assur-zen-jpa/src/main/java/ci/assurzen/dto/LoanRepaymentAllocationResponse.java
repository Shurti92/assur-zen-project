package ci.assurzen.dto;

public record LoanRepaymentAllocationResponse(
        Long installmentId,
        Integer installmentNumber,
        Long allocatedAmount
) {
}
