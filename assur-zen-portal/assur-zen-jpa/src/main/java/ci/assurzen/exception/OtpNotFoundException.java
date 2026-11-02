package ci.assurzen.exception;

public class OtpNotFoundException extends RuntimeException {

    public OtpNotFoundException() {
        super("Aucun code de verification en attente pour ce numero");
    }
}
