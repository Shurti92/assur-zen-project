package ci.assurzen.dto;

public record SmsSendResult(boolean success, int httpStatus, String body) {
}
