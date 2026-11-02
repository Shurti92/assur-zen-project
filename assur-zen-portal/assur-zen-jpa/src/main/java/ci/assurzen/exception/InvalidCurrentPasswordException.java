package ci.assurzen.exception;

/**
 * Mot de passe actuel incorrect lors d'un changement de mot de passe (PATCH /auth/change-password,
 * 2026-10-05). Distinct d'InvalidCredentialsException (login, 401) : un 401 ici declenchait un
 * renouvellement de session inutile cote applications, alors que l'utilisateur est bien connecte.
 * 400, code stable INVALID_CURRENT_PASSWORD (AuthExceptionHandler).
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public static final String CODE = "INVALID_CURRENT_PASSWORD";

    public InvalidCurrentPasswordException() {
        super("Mot de passe actuel incorrect");
    }
}
