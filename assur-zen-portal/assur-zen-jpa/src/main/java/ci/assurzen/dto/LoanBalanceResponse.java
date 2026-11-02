package ci.assurzen.dto;

public record LoanBalanceResponse(
        Long loanId,
        Long principalAmount,
        Long totalAmount,
        Long totalRepaid,
        Long remainingBalance
) {
}
