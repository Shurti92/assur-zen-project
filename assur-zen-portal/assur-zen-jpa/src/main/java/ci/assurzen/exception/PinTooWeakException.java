package ci.assurzen.exception;

public class PinTooWeakException extends RuntimeException {
    public PinTooWeakException() {
        super("Ce code PIN est trop simple. Évitez les suites (123456) et les chiffres répétés (111111).");
    }
}
