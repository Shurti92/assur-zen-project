package ci.assurzen.exception;

public class PartialPaymentAmountExceedsCapException extends RuntimeException {

    public PartialPaymentAmountExceedsCapException(long cap) {
        super("Le montant demande depasse le plafond autorise de 40% (plafond actuel: " + cap + ")");
    }
}
