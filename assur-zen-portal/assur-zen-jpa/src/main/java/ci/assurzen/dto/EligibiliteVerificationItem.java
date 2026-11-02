package ci.assurzen.dto;

public record EligibiliteVerificationItem(
        String critere,
        boolean satisfait,
        String detail
) {
}
