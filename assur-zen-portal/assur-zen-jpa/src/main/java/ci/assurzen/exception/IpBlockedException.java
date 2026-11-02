package ci.assurzen.exception;

import java.time.Instant;

public class IpBlockedException extends RuntimeException {

    private final Instant blockedUntil;

    public IpBlockedException(Instant blockedUntil) {
        super("Adresse IP temporairement bloquee suite a trop de tentatives de connexion echouees");
        this.blockedUntil = blockedUntil;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }
}
