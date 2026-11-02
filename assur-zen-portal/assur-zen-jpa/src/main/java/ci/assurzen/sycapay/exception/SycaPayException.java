package ci.assurzen.sycapay.exception;

/** Racine des exceptions techniques SycaPay (unchecked, comme SmsSendException). Ne doit jamais
 * porter de message contenant apiKey/secretKey/token dans son message (cf. PayloadRedactor cote
 * audit HTTP entrant - aucune protection equivalente n'existe cote appels SORTANTS, c'est aux
 * appelants de ce type de veiller a ne jamais y inclure de secret). */
public class SycaPayException extends RuntimeException {

    public SycaPayException(String message) {
        super(message);
    }

    public SycaPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
