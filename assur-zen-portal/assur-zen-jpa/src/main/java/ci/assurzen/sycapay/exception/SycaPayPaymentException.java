package ci.assurzen.sycapay.exception;

/** Erreur technique (reseau, HTTP inattendu, desserialisation) lors d'un appel de paiement. Ne doit
 * JAMAIS etre levee pour un resultat metier incertain (timeout, 5xx) : ce cas doit aboutir a un statut
 * UNKNOWN persiste, pas a une exception qui masquerait l'incertitude a l'appelant. Reservee aux cas ou
 * l'appel n'a manifestement pas pu atteindre SycaPay (ex: configuration invalide en amont). */
public class SycaPayPaymentException extends SycaPayException {

    public SycaPayPaymentException(String message) {
        super(message);
    }

    public SycaPayPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
