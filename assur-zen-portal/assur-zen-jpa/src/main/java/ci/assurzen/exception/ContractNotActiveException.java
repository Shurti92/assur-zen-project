package ci.assurzen.exception;

public class ContractNotActiveException extends RuntimeException {
    public ContractNotActiveException() {
        super("Cette demande n'est possible que pour un contrat actif.");
    }
}
