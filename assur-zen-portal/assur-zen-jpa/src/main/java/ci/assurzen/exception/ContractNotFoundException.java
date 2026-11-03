package ci.assurzen.exception;

/** Contrat inexistant OU appartenant a un tiers (404 volontairement identique, mobile 2026-11-02). */
public class ContractNotFoundException extends RuntimeException {
    public ContractNotFoundException() {
        super("Contrat introuvable.");
    }
}
