package ci.assurzen.exception;

public class PhoneAlreadyRegisteredException extends RuntimeException {
    public PhoneAlreadyRegisteredException() {
        super("Un compte existe déjà pour ce numéro. Connectez-vous ou réinitialisez votre code PIN.");
    }
}
