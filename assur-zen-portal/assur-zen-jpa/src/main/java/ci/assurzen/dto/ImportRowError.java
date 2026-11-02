package ci.assurzen.dto;

public record ImportRowError(
        int line,
        String username,
        String message
) {
}
