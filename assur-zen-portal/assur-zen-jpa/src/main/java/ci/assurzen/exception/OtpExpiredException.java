package ci.assurzen.exception;

public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException() {
        super("Le code de verification a expire, veuillez en redemander un");
    }
}
