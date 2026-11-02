package ci.assurzen.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Refus metier d'une demande de paiement de commission d'un commercial (2026-09-25) : code stable
 * (NOTHING_TO_PAY, AMOUNT_EXCEEDS_BALANCE, INVALID_AMOUNT...) et message en francais affiche tel quel
 * par le frontend. */
@Getter
public class CommercialPaymentRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CommercialPaymentRequestException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
