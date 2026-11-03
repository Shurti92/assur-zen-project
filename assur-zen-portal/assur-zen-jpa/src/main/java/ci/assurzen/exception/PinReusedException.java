package ci.assurzen.exception;

public class PinReusedException extends RuntimeException {
    public PinReusedException() {
        super("Choisissez un code PIN différent de l'actuel.");
    }
}
