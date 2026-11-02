package ci.assurzen.security;

import java.io.Serial;
import java.io.Serializable;

public record AuthenticatedUserPrincipal(
        Long userId,
        String username,
        String roles
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
