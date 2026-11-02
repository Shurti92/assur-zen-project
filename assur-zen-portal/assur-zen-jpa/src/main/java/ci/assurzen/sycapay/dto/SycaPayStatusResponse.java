package ci.assurzen.sycapay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reponse brute de POST /getStatusByOrderID.php. ATTENTION : "code" vaut 0 aussi bien pour une
 * transaction reussie QUE pour une transaction en echec (exemple reel Postman : code=0,
 * status="failed") - le statut definitif se lit UNIQUEMENT sur "status", jamais sur "code". Un code
 * negatif (-32/-33) signifie que la reference/commande est introuvable, ce qui n'est pas forcement un
 * echec definitif : la transaction peut simplement ne pas encore etre indexee cote SycaPay.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SycaPayStatusResponse(
        Integer code,
        String status,
        String datetransfer,
        String service,
        String montant,
        String referenceid,
        String transactionid,
        String contact,
        String orderid,
        String details,
        String description
) {
    /** Correctif 2026-09-23 : un code introuvable (-32/-33) prime TOUJOURS sur "status". Constate en
     * reel (reconciliation de DEMO-UNKNOWN) : SycaPay renvoie code=-33 AVEC status="failed" pour une
     * commande qu'il ne connait pas - lu sur "status" seul, une transaction simplement pas encore
     * indexee cote SycaPay passait en FAILED, donc re-payable : risque de double virement. */
    public boolean isSuccess() {
        return !isNotFound() && "success".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return !isNotFound() && "failed".equalsIgnoreCase(status);
    }

    /** -32 "Reference is undefined or not found" / -33 "Order not found / does not exist". */
    public boolean isNotFound() {
        return code != null && (code == -32 || code == -33);
    }
}
